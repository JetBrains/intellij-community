// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.util.io.DataInputOutputUtilRt;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.function.IntUnaryOperator;

@ApiStatus.Internal
final class IntEnumerator {
  private final Int2IntMap myEnumerates;
  private final IntList myIds;
  private int myNext;

  IntEnumerator() {
    this(true);
  }

  private IntEnumerator(boolean forSavingStub) {
    myEnumerates = forSavingStub ? new Int2IntOpenHashMap(1) : null;
    myIds = new IntArrayList();
  }

  int enumerate(int number) {
    assert myEnumerates != null;
    int i = myEnumerates.get(number);
    if (i == 0) {
      i = myNext;
      myEnumerates.put(number, myNext++);
      myIds.add(number);
    }
    return i;
  }

  int valueOf(int id) {
    return myIds.getInt(id);
  }

  void dump(@NotNull DataOutput stream) throws IOException {
    dump(stream, IntUnaryOperator.identity());
  }

  void dump(@NotNull DataOutput stream, @NotNull IntUnaryOperator idRemapping) throws IOException {
    DataInputOutputUtilRt.writeINT(stream, myIds.size());
    int[] elements = new int[myIds.size()];
    myIds.getElements(0, elements, 0, elements.length);
    for (int id : elements) {
      int remapped = idRemapping.applyAsInt(id);
      if (remapped == 0) {
        throw new IOException("remapping is not found for " + id);
      }
      DataInputOutputUtilRt.writeINT(stream, remapped);
    }
  }

  static IntEnumerator read(@NotNull DataInput stream) throws IOException {
    int size = DataInputOutputUtilRt.readINT(stream);
    IntEnumerator enumerator = new IntEnumerator(false);
    for (int i = 0; i < size; i++) {
      enumerator.myIds.add(DataInputOutputUtilRt.readINT(stream));
    }
    return enumerator;
  }
}
