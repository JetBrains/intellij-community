// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage.api;

import com.intellij.openapi.util.Pair;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

public class NoveltyImpl implements Novelty, Closeable {
  private static final int INITIAL_SIZE = 1024 * 1024 * 2047; // almost 2GB

  private final MappedByteBuffer myByteBuffer;
  private final List<Pair<Integer, Integer>> myFreeList;
  private int myFreeMemorySize;

  public NoveltyImpl(File backedFile) throws IOException {
    myFreeList = new LinkedList<>();
    myFreeMemorySize = INITIAL_SIZE;
    myFreeList.add(Pair.create(INITIAL_SIZE, 0));
    try (RandomAccessFile file = new RandomAccessFile(backedFile, "rw")) {
      myByteBuffer = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, INITIAL_SIZE);
    }
  }

  private void addFreeBlock(int blockSize, int blockPos) {
    for (int i = 0; i < myFreeList.size(); i++) {
      final Pair<Integer, Integer> block = myFreeList.get(i);
      final int size = block.first;
      if (blockSize <= size) {
        myFreeList.add(i, Pair.create(blockSize, blockPos));
        return;
      }
    }
    myFreeList.add(myFreeList.size(), Pair.create(blockSize, blockPos));
  }

  @Override
  public long alloc(byte[] bytes) {
    for (int i = 0; i < myFreeList.size(); i++) {
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
    }
    throw new OutOfMemoryError("Not enough memory in Novelty storage");
  }

  @Override
  public void free(long address) {
    myByteBuffer.position((int)address);
    final int size = myByteBuffer.getInt();
    addFreeBlock(size, (int)address);
    myFreeMemorySize += size;
  }

  @Override
  public byte[] lookup(long address) {
    myByteBuffer.position((int)address);
    int count = myByteBuffer.getInt();
    byte[] result = new byte[count];
    myByteBuffer.get(result);
    return result;
  }

  @Override
  public void update(long address, byte[] bytes) {
    myByteBuffer.position((int)address);
    final int count = myByteBuffer.getInt();
    assert bytes.length == count;
    myByteBuffer.put(bytes);
  }

  @Override
  public void close() {
    myByteBuffer.force();
  }

  public int getSize() {
    return INITIAL_SIZE - myFreeMemorySize;
  }
}
