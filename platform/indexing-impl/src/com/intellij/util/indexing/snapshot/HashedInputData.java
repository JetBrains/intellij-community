// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.snapshot;

import com.intellij.util.indexing.impl.InputData;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class HashedInputData<Key, Value> extends InputData<Key, Value> {
  private final int myHashId;

  protected HashedInputData(@NotNull Map<Key, Value> values, int hashId) {
    super(values);
    myHashId = hashId;
  }

  public int getHashId() {
    return myHashId;
  }
}
