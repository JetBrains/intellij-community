// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage;

import com.intellij.openapi.util.Computable;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.indexing.impl.ChangeTrackingValueContainer;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;

final class TransientChangeTrackingValueContainer<Value> extends ChangeTrackingValueContainer<Value> {
  TransientChangeTrackingValueContainer(@NotNull Computable<? extends ValueContainer<Value>> initializer) {
    super(initializer);
  }

  // Resets diff of index value for particular fileId
  void dropAssociatedValue(int inputId) {
    dropMergedData();

    removeFromAdded(inputId);
    if (myInvalidated != null) myInvalidated.remove(inputId);
  }

  @Override
  public void saveTo(DataOutput out, DataExternalizer<? super Value> externalizer) {
    throw new UnsupportedOperationException();
  }
}
