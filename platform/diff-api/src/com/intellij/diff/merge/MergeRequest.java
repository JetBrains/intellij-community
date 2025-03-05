// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see com.intellij.diff.DiffRequestFactory
 */
public abstract class MergeRequest implements UserDataHolder {
  protected final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();

  public abstract @NlsContexts.DialogTitle @Nullable String getTitle();

  /**
   * Called on conflict resolve end. Should be called exactly once for each request that was shown.
   * <p>
   * MergeRequest should keep the initial state of its content and restore it on {@link MergeResult#CANCEL}
   */
  @RequiresEdt
  public abstract void applyResult(@NotNull MergeResult result);

  @RequiresEdt
  public void onAssigned(boolean assigned) {
  }

  @Override
  public @Nullable <T> T getUserData(@NotNull Key<T> key) {
    return myUserDataHolder.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myUserDataHolder.putUserData(key, value);
  }
}
