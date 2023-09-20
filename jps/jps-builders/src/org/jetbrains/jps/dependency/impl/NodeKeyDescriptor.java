// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.SerializableGraphElement;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class NodeKeyDescriptor<T extends SerializableGraphElement> implements KeyDescriptor<T> {
  public static @NotNull <T extends SerializableGraphElement> Set<T> createNodeSet() {
    return new HashSet<>();
  }

  @Override
  public int getHashCode(final T value) {
    return value.hashCode();
  }

  @Override
  public boolean isEqual(final T val1, final T val2) {
    return val1.equals(val2);
  }

  @Override
  public void save(final @NotNull DataOutput storage, final @NotNull T value) throws IOException {
    SerializerRegistryImpl.getInstance().getSerializer(0).write(value, storage);
  }

  @Override
  public T read(final @NotNull DataInput storage) throws IOException {
    return SerializerRegistryImpl.getInstance().getSerializer(0).read(storage);
  }
}