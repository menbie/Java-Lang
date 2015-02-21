package net.openhft.chronicle.bytes;

import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static net.openhft.chronicle.core.UnsafeMemory.MEMORY;

public class NativeStore implements BytesStore<NativeStore> {
    private static final long MEMORY_MAPPED_SIZE = 128 << 10;
    private final Cleaner cleaner;
    private final Deallocator deallocator;
    private final AtomicInteger refCount = new AtomicInteger(1);
    private final Object obj;
    private long address, capacity;

    NativeStore(ByteBuffer bb) {
        obj = bb;
        this.address = ((DirectBuffer) bb).address();
        this.capacity = bb.capacity();
        cleaner = null;
        deallocator = null;
    }

    private NativeStore(long capacity, boolean zeroOut) {
        this(MEMORY.allocate(capacity), capacity, zeroOut);
    }

    private NativeStore(long address, long capacity, boolean zeroOut) {
        this.address = address;
        this.capacity = capacity;
        //        System.out.println("old value " + Integer.toHexString(NativeBytes.UNSAFE.getInt(null, address)));
        if (zeroOut || capacity < MEMORY_MAPPED_SIZE) {
            MEMORY.setMemory(address, capacity, (byte) 0);
            MEMORY.storeFence();
        }

        deallocator = new Deallocator(address);
        cleaner = Cleaner.create(this, deallocator);
        obj = null;
    }

    public static NativeStore of(long capacity) {
        return new NativeStore(capacity, true);
    }

    public static NativeStore ofLazy(long capacity) {
        return new NativeStore(capacity, false);
    }

    @Override
    public long capacity() {
        return capacity;
    }

    @Override
    public void storeFence() {
        MEMORY.storeFence();
    }

    @Override
    public void loadFence() {
        MEMORY.loadFence();
    }

    @Override
    public void reserve() {
        refCount.incrementAndGet();
    }


    @Override
    public void release() {
        int value = refCount.decrementAndGet();
        if (value < 0)
            throw new IllegalStateException();
        if (value == 0)
            cleaner.clean();
    }

    @Override
    public int refCount() {
        return refCount.get();
    }

    @Override
    public byte readByte(long offset) {
        return MEMORY.readByte(address + offset);
    }

    @Override
    public short readShort(long offset) {
        return MEMORY.readShort(address + offset);
    }

    @Override
    public int readInt(long offset) {
        return MEMORY.readInt(address + offset);
    }

    @Override
    public long readLong(long offset) {
        return MEMORY.readLong(address + offset);
    }

    @Override
    public float readFloat(long offset) {
        return MEMORY.readFloat(address + offset);
    }

    @Override
    public double readDouble(long offset) {
        return MEMORY.readDouble(address + offset);
    }

    @Override
    public NativeStore writeByte(long offset, byte i8) {
        MEMORY.writeByte(address + offset, i8);
        return this;
    }

    @Override
    public NativeStore writeShort(long offset, short i16) {
        MEMORY.writeShort(address + offset, i16);
        return this;
    }

    @Override
    public NativeStore writeInt(long offset, int i32) {
        MEMORY.writeInt(address + offset, i32);
        return this;
    }

    @Override
    public NativeStore writeOrderedInt(long offset, int i) {
        MEMORY.writeOrderedInt(address + offset, i);
        return this;
    }

    @Override
    public NativeStore writeLong(long offset, long i64) {
        MEMORY.writeLong(address + offset, i64);
        return this;
    }

    @Override
    public NativeStore writeFloat(long offset, float f) {
        MEMORY.writeFloat(address + offset, f);
        return this;
    }

    @Override
    public NativeStore writeDouble(long offset, double d) {
        MEMORY.writeDouble(address + offset, d);
        return this;
    }

    @Override
    public NativeStore write(long offsetInRDO, byte[] bytes, int offset, int length) {
        MEMORY.copyMemory(bytes, offset, address + offsetInRDO, length);
        return this;
    }

    @Override
    public boolean compareAndSwapInt(long offset, int expected, int value) {
        return MEMORY.compareAndSwapInt(address + offset, expected, value);
    }

    @Override
    public boolean compareAndSwapLong(long offset, long expected, long value) {
        return MEMORY.compareAndSwapLong(address + offset, expected, value);
    }

    @Override
    public NativeStore writeOrderedLong(long offset, long i) {
        MEMORY.writeOrderedLong(address + offset, i);
        return this;
    }

    @Override
    public NativeStore write(long offsetInRDO, ByteBuffer bytes, int offset, int length) {
        if (bytes.isDirect()) {
            MEMORY.copyMemory(((DirectBuffer) bytes).address(), address + offsetInRDO, length);
        } else {
            MEMORY.copyMemory(bytes.array(), offset, address + offsetInRDO, length);
        }
        return this;
    }

    static class Deallocator implements Runnable {
        private volatile long address;

        Deallocator(long address) {
            assert address != 0;
            this.address = address;
        }

        @Override
        public void run() {
            if (address == 0)
                return;
            MEMORY.freeMemory(address);
            address = 0;
        }
    }
}
