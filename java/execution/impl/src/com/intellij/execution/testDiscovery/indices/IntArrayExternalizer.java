// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.indices;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

class IntArrayExternalizer implements DataExternalizer<TIntArrayList> {
  static final IntArrayExternalizer INSTANCE = new IntArrayExternalizer();

  @Override
  public void save(@NotNull DataOutput out, TIntArrayList value) throws IOException {
    DataInputOutputUtil.writeINT(out, value.size());
    for (int i = 0; i < value.size(); i++) {
      DataInputOutputUtil.writeINT(out, value.get(i));
    }
  }

  @Override
  public TIntArrayList read(@NotNull DataInput in) throws IOException {
    int size = DataInputOutputUtil.readINT(in);
    TIntArrayList array = new TIntArrayList(size);
    for (int i = 0; i < size; i++) {
      array.add(DataInputOutputUtil.readINT(in));
    }
    return array;
  }
}
