/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.refResolve;

import com.intellij.openapi.Disposable;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/**
 * the (int -> int[]) map which is persisted to the specified file.<p>
 * File layout:<p>
 * <img src="PersistentIntList.png"/>
 * <p>(to edit the diagram go to www.draw.io, "Import from", this PersistentIntList.png)</p>
 */
class PersistentIntList implements Disposable {
  public static final int MAX_DATA_BYTES =  500*1000*1000;
  public static final int MAX_LIST_LENGTH = 100*1000*1000;
  private final FileChannel data;
  public int gap; // bytes lost due to fragmentation
  private IntArray pointers;

  public PersistentIntList(@NotNull File dataFile, int initialSize) throws IOException {
    data = new RandomAccessFile(dataFile, "rw").getChannel();
    int pointersBase;
    int initialCapacity = Math.min((initialSize+1)*2, initialSize + 256);
    if (initialSize == 0) {
      pointersBase = readInt(data, 0);
    }
    else {
      writeInt(data, 0, 4); // base of the pointers array
      writeInt(data, 4, initialSize);
      writeInt(data, 8, initialCapacity);
      fillWithZeros(data, 4 + 8, initialCapacity *4);
      pointersBase = 4;
    }
    pointers = new IntArray(data, pointersBase);
    if (initialSize != 0) {
      assert pointers.size == initialSize;
      assert pointers.capacity == initialCapacity;
      assert pointers.base == 4;
    }
    EMPTY = new Empty(data);
  }

  public synchronized int getSize() {
    return pointers.size;
  }

  private static void fillWithZeros(FileChannel data, int from, int length) throws IOException {
    ByteBuffer zeros = ByteBuffer.allocateDirect(Math.min(8192, length));

    while (length > 0) {
      ByteBuffer toWrite = length < zeros.limit() ? ByteBuffer.allocateDirect(length) : zeros;
      toWrite.position(0);
      int written = data.write(toWrite, from);
      length -= written;
      from += written;
    }
  }

  private static void writeInt(FileChannel data, int off, int value) throws IOException {
    ByteBuffer b = ByteBuffer.allocate(4);
    b.putInt(0,value);
    data.write(b, off);
  }
  private static int readInt(FileChannel data, int off) throws IOException {
    ByteBuffer b = ByteBuffer.allocate(4);
    int read = data.read(b, off);
    if (read != 4) throw new IOException(read + " bytes instead of 4");
    return b.getInt(0);
  }

  private static class Empty extends IntArray{
    public Empty(FileChannel data) {
      super(data);
    }

    @Override
    public int[] toArray() {
      return ArrayUtil.EMPTY_INT_ARRAY;
    }

    @Override
    void assertListLength() {
    }
  }

  private final Empty EMPTY;

  private static class IntArray {
    private final FileChannel data;
    private final int base;
    private int size;
    private final int capacity;

    public IntArray(FileChannel data, int base) throws IOException {
      this.data = data;
      this.base = base;
      size = readInt(data, base);
      capacity = readInt(data, base + 4);
      assertListLength();
    }

    private IntArray(FileChannel data) {
      this.data = data;
      base = 0;
      size = 0;
      capacity = 0;
    }

    public int get(int i) throws IOException {
      if (i < 0 || i >= size) throw new IndexOutOfBoundsException("i="+i+"; size="+size);
      return readInt(data, base + 8 + i*4);
    }

    public void put(int i, int value) throws IOException {
      if (i < 0 || i >= size) throw new IndexOutOfBoundsException("i="+i+"; size="+size);
      writeInt(data, base + 8 + i * 4, value);
    }

    public IntArray addAll(int[] values) throws IOException {
      int[] old = toArray();
      assertSorted(old);
      assertListLength();

      ByteBuffer mergedBytes = ByteBuffer.allocateDirect(size*4 + values.length * 4);
      int i = 0;
      int j = 0;
      while (i < size || j < values.length) {
        int stored = i < size ? old[i] : Integer.MAX_VALUE;
        int value = j < values.length ? values[j] : Integer.MAX_VALUE;
        if (stored < value) {
          mergedBytes.putInt(stored);
          i++;
        }
        else if (stored > value) {
          mergedBytes.putInt(value);
          j++;
        }
        else {
          mergedBytes.putInt(value);
          j++;
          i++;
        }
      }
      mergedBytes.limit(mergedBytes.position());
      mergedBytes.position(0);

      int[] mergedInts = fromBytes(mergedBytes);
      assertSorted(mergedInts);

      int newSize = mergedInts.length;
      if (newSize > capacity) {
        IntArray realloc = reallocWith(mergedBytes, newSize);
        assert realloc.size == newSize;
        return realloc;
      }
      data.write(mergedBytes, base + 8);
      writeInt(data, base, newSize);
      size = newSize;
      assertListLength();

      return null;
    }

    private IntArray reallocWith(ByteBuffer bytes, int maxSize) throws IOException {
      assert maxSize > 0 && maxSize < MAX_LIST_LENGTH : maxSize;
      int newSize = Math.max(maxSize, bytes.limit() / 4);
      int newCapacity = newSize < 10 ? (newSize + 1) * 2 : newSize * 3 / 2;
      int newBase = (int)data.size();
      writeInt(data, newBase, newSize);
      writeInt(data, newBase + 4, newCapacity);
      bytes.position(0);
      data.write(bytes, newBase + 8);
      fillWithZeros(data, newBase + 8 + newSize * 4, (newCapacity - newSize) * 4);
      IntArray array = new IntArray(data, newBase);
      assert array.size == newSize;
      assert array.capacity == newCapacity;
      assert array.base == newBase;
      array.assertListLength();
      return array;
    }


    public int[] toArray() throws IOException {
      return fromBytes(toBuffer());
    }

    private ByteBuffer toBuffer() throws IOException {
      assertListLength();
      int listLength = size;
      ByteBuffer bytes = ByteBuffer.allocateDirect(listLength * 4);
      int read = data.read(bytes, base+8);
      if (read != listLength*4) throw new IOException(read +" instead of "+listLength*4);
      bytes.position(0);
      assert bytes.limit() == listLength * 4;
      return bytes;
    }

    void assertListLength() {
      int listLength = size;
      assert 0 <= listLength && listLength <= MAX_LIST_LENGTH : "size = "+listLength + ", capacity=" + capacity;
      assert 0 < capacity && capacity <= MAX_LIST_LENGTH : "size = "+listLength + ", capacity=" + capacity;
      assert capacity >= listLength : "size = "+listLength + ", capacity=" + capacity;
      assert listLength == 0 || capacity <= (listLength+1)*2 : "size = "+listLength + ", capacity=" + capacity;
    }
  }

  @Override
  public void dispose() {
    try {
      data.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public synchronized int[] get(final int id) {
    assertPointer(id);
    try {
      if (id >= pointers.size) {
        return ArrayUtil.EMPTY_INT_ARRAY;
      }
      int arrayBase = pointers.get(id);
      IntArray array = arrayBase == 0 ? EMPTY : new IntArray(data, arrayBase);
      return array.toArray();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public synchronized void addAll(final int id, @NotNull final int[] values) {
    assert 0 < values.length && values.length <= MAX_LIST_LENGTH : values.length;
    assert id > 0;
    Arrays.sort(values);
    try {
      if (id >= pointers.size) {
        pointers = pointers.reallocWith(pointers.toBuffer(), id+1);
        writeInt(data, 0, pointers.base);
        assert pointers.size > id : id + " > " + pointers.size;
      }
      int arrayBase = pointers.get(id);
      IntArray array = arrayBase == 0 ? EMPTY : new IntArray(data, arrayBase);
      IntArray newArray = array.addAll(values);
      if (newArray != null) {
        pointers.put(id, newArray.base);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    int[] ids = get(id);
    assertSorted(ids);
    TIntHashSet set = new TIntHashSet(ids);
    assert set.containsAll(values): "ids: "+Arrays.toString(ids)+";\n values:"+Arrays.toString(values);
  }

  private static void assertSorted(int[] oldIds) {
    for (int i = 1; i < oldIds.length; i++) {
      assert oldIds[i - 1] < oldIds[i] : oldIds[i-1] + ", " + oldIds[i];
    }
  }

  private static int[] fromBytes(@NotNull ByteBuffer bytes) {
    IntBuffer intBuffer = bytes.asIntBuffer();
    int[] result = new int[intBuffer.limit()];
    intBuffer.get(result);
    return result;
  }

  private static void assertPointer(int pointer) {
    assert 0 < pointer && pointer <= MAX_DATA_BYTES : pointer;
  }

  public synchronized void flush() throws IOException {
    data.force(true);
  }
}
