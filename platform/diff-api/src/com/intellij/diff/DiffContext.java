// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DiffContext implements UserDataHolder, FocusableContext {
  protected final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();

  public abstract @Nullable Project getProject();

  public abstract boolean isWindowFocused();

  /**
   * @see com.intellij.diff.util.DiffUserDataKeys
   */
  @Override
  public @Nullable <T> T getUserData(@NotNull Key<T> key) {
    return myUserDataHolder.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myUserDataHolder.putUserData(key, value);
  }
}
