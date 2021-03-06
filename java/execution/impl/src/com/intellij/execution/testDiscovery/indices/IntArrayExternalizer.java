// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.indices;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

final class IntArrayExternalizer implements DataExternalizer<IntList> {
  static final IntArrayExternalizer INSTANCE = new IntArrayExternalizer();

  @Override
  public void save(@NotNull DataOutput out, IntList value) throws IOException {
    DataInputOutputUtil.writeINT(out, value.size());
    for (int i = 0; i < value.size(); i++) {
      DataInputOutputUtil.writeINT(out, value.getInt(i));
    }
  }

  @Override
  public IntList read(@NotNull DataInput in) throws IOException {
    int size = DataInputOutputUtil.readINT(in);
    IntList array = new IntArrayList(size);
    for (int i = 0; i < size; i++) {
      array.add(DataInputOutputUtil.readINT(in));
    }
    return array;
  }
}
