// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.diff.FocusableContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class MergeContext implements UserDataHolder, FocusableContext {
  protected final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();

  public abstract @Nullable Project getProject();

  /**
   * Called by MergeTool on conflict resolve end. Should delegate to the {@link MergeRequest#applyResult(MergeResult)}
   */
  @RequiresEdt
  public abstract void finishMerge(@NotNull MergeResult result);

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
