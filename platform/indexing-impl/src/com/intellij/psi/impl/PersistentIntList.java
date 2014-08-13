/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.Bits;
import com.intellij.util.io.IntToIntBtree;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.RandomAccessDataFile;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntProcedure;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * the (int -> int[]) map which is persisted to the specified file.
 */
public class PersistentIntList implements Disposable {
  public static final int MAX_DATA_BYTES = 500000000;
  public static final int MAX_LIST_LENGTH = 100000;
  private final IntToIntBtree index;
  private RandomAccessDataFile data;
  public int gap; // bytes lost due to fragmentation
  private final int dataStart; // offset of real data; the bytes before are reserved for 'index' meta information, see persistsVarsTo()

  public PersistentIntList(@NotNull File indexFile, @NotNull File dataFile, boolean initial) throws IOException {
    if (initial) {
      FileUtil.writeToFile(dataFile, ArrayUtil.EMPTY_BYTE_ARRAY);
    }
    PagedFileStorage.StorageLockContext context = new PagedFileStorage.StorageLockContext(true);
    context.lock();
    try {
      data = new RandomAccessDataFile(dataFile);
      index = new IntToIntBtree(4096, indexFile, context, initial);
      dataStart = persistsVarsTo(data, initial);
    }
    finally {
      context.unlock();
    }
  }

  private int persistsVarsTo(@NotNull final RandomAccessDataFile data, boolean toDisk) {
    return index.persistVars(new IntToIntBtree.BtreeDataStorage() {
      @Override
      public int persistInt(int offset, int value, boolean toDisk) {
        if (toDisk) {
          data.putInt(offset, value);
          return value;
        }
        else {
          return data.getInt(offset);
        }
      }
    }, toDisk);
  }

  @Override
  public void dispose() {
    index.withStorageLock(new Runnable() {
      @Override
      public void run() {
        try {
          persistsVarsTo(data, true);
          index.doClose();
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        data.dispose();
      }
    });
  }

  @NotNull
  public int[] get(final int id) {
    final Ref<int[]> res = new Ref<int[]>();

    index.withStorageLock(new Runnable() {
      @Override
      public void run() {
        final int[] ptrPtr = new int[1];
        boolean exists = index.get(id, ptrPtr);
        if (!exists) {
          ptrPtr[0] = 0;
        }
        int pointer = ptrPtr[0];
        if (pointer == 0) {
          res.set(ArrayUtil.EMPTY_INT_ARRAY);
        }
        else {
          assertPointer(pointer);
          int listLength = data.getInt(pointer);
          int capacity = data.getInt(pointer + 4);
          assertListLength(listLength, capacity);
          int[] result = new int[listLength];
          byte[] bytes = new byte[listLength * 4];
          data.get(pointer + 8, bytes, 0, bytes.length);
          for (int i = 0; i < listLength; i++) {
            result[i] = Bits.getInt(bytes, i*4);
          }
          res.set(result);
        }
      }
    });
    return res.get();
  }

  // return true if was added
  public boolean add(final int id, final int value) {
    assert value > 0;
    assert id > 0;
    final boolean[] added = new boolean[1];
    index.withStorageLock(new Runnable() {
      @Override
      public void run() {
        int[] ptrPtr = new int[1];
        index.get(id, ptrPtr);
        final int pointer = ptrPtr[0];
        int[] stored;
        int capacity;
        final int listLength;
        if (pointer == 0) {
          stored = ArrayUtil.EMPTY_INT_ARRAY;
          listLength = 0;
          capacity = 2;
        }
        else {
          assertPointer(pointer);
          listLength = data.getInt(pointer);
          capacity = data.getInt(pointer+4);
          assertListLength(listLength,capacity);
          stored = new int[listLength];
          for (int i = 0; i < listLength; i++) {
            int v = data.getInt(pointer + (i + 2) * 4);
            stored[i] = v;
            if (v == value) return;
          }
          // append
          if (capacity > listLength /*|| data.length() == pointer + 4 + 4 + 4*capacity*/) {
            data.putInt(pointer + (listLength + 2) * 4, value);
            data.putInt(pointer, listLength + 1);
            if (capacity <= listLength) {
              data.putInt(pointer+4, capacity + 1);
            }
            added[0] = true;
            return;
          }
          // reallocate
          gap += 4 + 4 + 4 * capacity;
        }

        int storePointer = (int)data.length();
        data.putInt(storePointer, stored.length + 1);
        int newCapacity = capacity < 10 ? capacity * 2 : (int)(capacity * 1.5);
        assert newCapacity > stored.length + 1;
        data.putInt(storePointer+4, newCapacity);
        for (int i = 0; i < stored.length; i++) {
          int v = stored[i];
          data.putInt(storePointer + (i+2)*4, v);
        }
        data.putInt(storePointer + (stored.length+2)*4, value);
        for (int i = stored.length + 1; i < newCapacity; i++) {
          data.putInt(storePointer + (i+2)*4, 0); // gap
        }
        index.put(id, storePointer);
        if (storePointer > 10000000) {
          int i = 0;
        }
        added[0] = true;
      }
    });

    return added[0];
  }

  private static void assertListLength(int listLength, int capacity) {
    assert 0 < listLength && listLength <= MAX_LIST_LENGTH : listLength;
    assert 0 < capacity && capacity <= MAX_LIST_LENGTH : capacity;
    assert capacity >= listLength : listLength + ", " + capacity;
    assert capacity <= (listLength+1)*2 : listLength + ", " + capacity;
  }

  public void addAll(final int id, @NotNull final int[] values) {
    assertListLength(values.length, values.length);
    assert id > 0;
    Arrays.sort(values);

    index.withStorageLock(new Runnable() {
      @Override
      public void run() {
        int[] ptrPtr = new int[1];
        index.get(id, ptrPtr);
        final int pointer = ptrPtr[0];
        int capacity;
        final int newListLength;
        byte[] mergedBytes;

        if (pointer == 0) {
          mergedBytes = toBytes(values);
          newListLength = values.length;
          capacity = 0;
        }
        else {
          int[] oldIds = get(id);
          checkSorted(oldIds);

          assertPointer(pointer);
          int storedListLength = data.getInt(pointer);
          capacity = data.getInt(pointer + 4);
          assertListLength(storedListLength, capacity);
          // try to merge inplace and if failed, reallocate at the end
          byte[] storedBytes = new byte[storedListLength * 4];
          data.get(pointer + 8, storedBytes, 0, storedListLength * 4);

          mergedBytes = new byte[storedBytes.length + values.length * 4];
          int outPtr = 0;
          int i = 0;
          int j = 0;
          while (i < storedListLength || j < values.length) {
            int stored = i < storedListLength ? Bits.getInt(storedBytes, i * 4) : Integer.MAX_VALUE;
            int value = j < values.length ? values[j] : Integer.MAX_VALUE;
            if (stored < value) {
              Bits.putInt(mergedBytes, outPtr, stored);
              outPtr += 4;
              i++;
            }
            else if (stored > value) {
              Bits.putInt(mergedBytes, outPtr, value);
              outPtr += 4;
              j++;
            }
            else {
              Bits.putInt(mergedBytes, outPtr, value);
              outPtr += 4;
              j++;
              i++;
            }
          }
          int[] mergedInts = fromBytes(mergedBytes, outPtr);
          checkSorted(mergedInts);

          newListLength = outPtr / 4;
          assertListLength(newListLength, newListLength);
          if (newListLength <= capacity) {
            storeArray(data, pointer, newListLength, capacity, mergedBytes);
            return;
          }
          gap += capacity * 4 + 8;
        }
        // reallocate at the end

        int storePointer = (int)data.length();
        assertPointer(storePointer);
        int oldCapacity = Math.max(capacity, newListLength);
        int newCapacity = oldCapacity < 10 ? (oldCapacity + 1) * 2 : (int)(oldCapacity * 1.5);
        assert newCapacity > newListLength + 1;
        storeArray(data, storePointer, newListLength, newCapacity, mergedBytes);
        index.put(id, storePointer);
      }
    });

    int[] ids = get(id);
    checkSorted(ids);
    TIntHashSet set = new TIntHashSet(ids);
    assert set.containsAll(values): "ids: "+Arrays.toString(ids)+";\n values:"+Arrays.toString(values);
  }

  private static void checkSorted(int[] oldIds) {
    for (int i = 1; i < oldIds.length; i++) {
      assert oldIds[i - 1] < oldIds[i] : oldIds[i-1] + ", " + oldIds[i];
    }
  }

  private static byte[] toBytes(@NotNull int[] values) {
    byte[] mergedBytes = new byte[4 * values.length];
    for (int i = 0; i < values.length; i++) {
      int value = values[i];
      Bits.putInt(mergedBytes, i * 4, value);
    }
    return mergedBytes;
  }

  private static int[] fromBytes(@NotNull byte[] bytes, int length) {
    assert length % 4 == 0;
    int[] ints = new int[length/4];
    for (int i = 0; i < length; i+=4) {
      int value = Bits.getInt(bytes, i);
      ints[i/4] = value;
    }
    return ints;
  }

  private static void storeArray(@NotNull RandomAccessDataFile data,
                                 int storePointer,
                                 int newListLength,
                                 int newCapacity,
                                 @NotNull byte[] mergedBytes) {
    assertListLength(newListLength, newCapacity);
    data.putInt(storePointer, newListLength);
    data.putInt(storePointer + 4, newCapacity);
    data.put(storePointer + 8, mergedBytes, 0, newListLength * 4);
    byte[] fill = new byte[(newCapacity - newListLength) * 4];
    Arrays.fill(fill, (byte)-1);
    data.put(storePointer + 8 + newListLength * 4, fill, 0, fill.length);
  }

  private static void assertPointer(int pointer) {
    assert 0 < pointer && pointer <= MAX_DATA_BYTES : pointer;
  }

  public void flush() {
    index.withStorageLock(new Runnable() {
      @Override
      public void run() {
        persistsVarsTo(data, true);
        index.doFlush();
        data.sync();
        //data.force();
      }
    });
  }

  private void compactIfNecessary() {
    if (gap < data.length() / 2) return;
    index.withStorageLock(new Runnable() {
      @Override
      public void run() {
        persistsVarsTo(data, true);
        index.doFlush();
        data.sync();

        try {
          final RandomAccessDataFile newData = new RandomAccessDataFile(new File(data.getFile().getParentFile(), "newData"));
          persistsVarsTo(newData, true);
          final TIntIntHashMap map = new TIntIntHashMap();
          index.processMappings(new IntToIntBtree.KeyValueProcessor() {
            @Override
            public boolean process(int key, int value) throws IOException {
              map.put(key, value);
              return true;
            }
          });
          map.forEachEntry(new TIntIntProcedure() {
            @Override
            public boolean execute(int key, int value) {
              int[] ids = get(key);
              int pointer = (int)newData.length();
              byte[] bytes = toBytes(ids);
              storeArray(newData, pointer, ids.length, (int)(ids.length * 1.3), bytes);
              index.put(key, pointer);
              return true;
            }
          });

          data.dispose();
          data = newData;
          gap = 0;
          flush();
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }
}
