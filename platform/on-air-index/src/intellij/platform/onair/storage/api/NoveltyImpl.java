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
  private static int INITIAL_SIZE = 1024 * 1024 * 2047; // almost 2GB

  private MappedByteBuffer myByteBuffer;
  private int myFreeOffset;
  private List<Pair<Integer, Integer>> myFreeList;

  public NoveltyImpl(File backedFile) throws IOException {
    myFreeList = new LinkedList<>();
    myFreeList.add(Pair.create(INITIAL_SIZE, 0));
    myFreeOffset = 0;
    try (RandomAccessFile file = new RandomAccessFile(backedFile, "rw")) {
      myByteBuffer = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, INITIAL_SIZE);
    }
  }

  @Override
  public long alloc(byte[] bytes) {
    if (myFreeOffset + 4 + bytes.length < myByteBuffer.capacity()) {
      long result = myFreeOffset;
      myByteBuffer.position(myFreeOffset);
      myByteBuffer.putInt(bytes.length);
      myByteBuffer.put(bytes);
      myFreeOffset = myByteBuffer.position();
      return result;
    } else {
      return 0;
    }
  }

  @Override
  public void free(long address) {
    // Not implemented yet
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
}
