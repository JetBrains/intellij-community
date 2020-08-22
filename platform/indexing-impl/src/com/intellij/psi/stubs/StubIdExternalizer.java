// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class StubIdExternalizer implements DataExternalizer<StubIdList> {
  public static final StubIdExternalizer INSTANCE = new StubIdExternalizer();

  @Override
  public void save(final @NotNull DataOutput out, final @NotNull StubIdList value) throws IOException {
    int size = value.size();
    if (size == 0) {
      DataInputOutputUtil.writeINT(out, Integer.MAX_VALUE);
    }
    else if (size == 1) {
      DataInputOutputUtil.writeINT(out, value.get(0)); // most often case
    }
    else {
      DataInputOutputUtil.writeINT(out, -size);
      for (int i = 0; i < size; ++i) {
        DataInputOutputUtil.writeINT(out, value.get(i));
      }
    }
  }

  @Override
  public @NotNull StubIdList read(final @NotNull DataInput in) throws IOException {
    int size = DataInputOutputUtil.readINT(in);
    if (size == Integer.MAX_VALUE) {
      return new StubIdList();
    }
    if (size >= 0) {
      return new StubIdList(size);
    }
    size = -size;
    int[] result = new int[size];
    for (int i = 0; i < size; ++i) {
      result[i] = DataInputOutputUtil.readINT(in);
    }
    return new StubIdList(result, size);
  }
}
