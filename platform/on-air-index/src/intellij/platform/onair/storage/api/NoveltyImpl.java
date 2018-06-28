// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage.api;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;

public class NoveltyImpl implements Novelty, Closeable {
  private static final int INITIAL_SIZE = 1024 * 1024 * 2047; // almost 2GB

  private final MappedByteBuffer myByteBuffer;
  // private final List<Pair<Integer, Integer>> myFreeList;
  private AtomicInteger mySize = new AtomicInteger(0);

  public NoveltyImpl(File backedFile) throws IOException {
    //myFreeList = new LinkedList<>();
    //myFreeMemorySize = INITIAL_SIZE;
    //myFreeList.add(Pair.create(INITIAL_SIZE, 0));
    try (RandomAccessFile file = new RandomAccessFile(backedFile, "rw")) {
      myByteBuffer = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, INITIAL_SIZE);
    }
  }

  private void addFreeBlock(int blockSize, int blockPos) {
    /*for (int i = 0; i < myFreeList.size(); i++) {
      final Pair<Integer, Integer> block = myFreeList.get(i);
      final int size = block.first;
      if (blockSize <= size) {
        myFreeList.add(i, Pair.create(blockSize, blockPos));
        return;
      }
    }
    myFreeList.add(myFreeList.size(), Pair.create(blockSize, blockPos));*/
  }

  @Override
  public long alloc(byte[] bytes) {
    /*for (int i = 0; i < myFreeList.size(); i++) {
      final Pair<Integer, Integer> block = myFreeList.get(i);
      final int size = block.first;
      final int pos = block.second;
      final int allocSize = 4 + bytes.length;
      if (size >= allocSize) {
        myByteBuffer.position(pos);
        myByteBuffer.putInt(bytes.length);
        myByteBuffer.put(bytes);
        myFreeList.remove(i);
        if (size - allocSize > 0) {
          addFreeBlock(size - allocSize, pos + allocSize);
        }
        myFreeMemorySize -= bytes.length;
        return pos;
      }
    }*/

    int size = mySize.getAndAdd(4 + bytes.length);

    if (size + 4 + bytes.length < myByteBuffer.capacity()) {
      ByteBuffer buffer = myByteBuffer.duplicate();
      buffer.position(size);
      buffer.putInt(bytes.length);
      buffer.put(bytes);
      return (long)size;
    }

    throw new OutOfMemoryError("Not enough memory in Novelty storage");
  }

  @Override
  public void free(long address) {
    /*myByteBuffer.position((int)address);
    final int size = myByteBuffer.getInt();
    addFreeBlock(size, (int)address);
    myFreeMemorySize += size;*/
  }

  @Override
  public byte[] lookup(long address) {
    ByteBuffer buffer = myByteBuffer.duplicate();
    buffer.position((int)address);
    int count = buffer.getInt();
    byte[] result = new byte[count];
    buffer.get(result);
    return result;
  }

  @Override
  public void update(long address, byte[] bytes) {
    ByteBuffer buffer = myByteBuffer.duplicate();
    buffer.position((int)address);
    final int count = buffer.getInt();
    assert bytes.length == count;
    buffer.put(bytes);
  }

  @Override
  public void close() {
    myByteBuffer.force();
  }

  public int getSize() {
    return mySize.get();
  }

  public int getFreeSpace() {
    return INITIAL_SIZE - getSize();
  }
}
