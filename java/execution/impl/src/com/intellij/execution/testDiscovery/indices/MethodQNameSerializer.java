// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.indices;

import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

class MethodQNameSerializer implements KeyDescriptor<Long> {
  public static final MethodQNameSerializer INSTANCE = new MethodQNameSerializer();

  private MethodQNameSerializer() {}

  @Override
  public void save(@NotNull DataOutput out, Long value) throws IOException {
    out.writeLong(value);
  }

  @Override
  public Long read(@NotNull DataInput in) throws IOException {
    return in.readLong();
  }

  @Override
  public int getHashCode(Long value) {
    return value.hashCode();
  }

  @Override
  public boolean isEqual(Long val1, Long val2) {
    return val1.equals(val2);
  }
}
