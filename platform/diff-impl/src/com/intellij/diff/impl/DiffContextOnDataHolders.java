// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl;

import com.intellij.diff.DiffContextEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public abstract class DiffContextOnDataHolders extends DiffContextEx {
  private final @NotNull UserDataHolder myInitialContext;
  private final @NotNull UserDataHolder myOwnContext = new UserDataHolderBase();

  public DiffContextOnDataHolders(@NotNull UserDataHolder initialContext) {
    myInitialContext = initialContext;
  }

  @Override
  public @Nullable <T> T getUserData(@NotNull Key<T> key) {
    T data = myOwnContext.getUserData(key);
    if (data != null) return data;
    return myInitialContext.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myOwnContext.putUserData(key, value);
  }
}
