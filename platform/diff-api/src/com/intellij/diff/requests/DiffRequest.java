// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.requests;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

/**
 * Represents some data that can be shown by a {@link com.intellij.diff.DiffTool}.
 * <p>
 * Ex: two files to compare, a three-way resolved merge - {@link ContentDiffRequest},
 * a standalone patch file - {@link com.intellij.openapi.vcs.changes.patch.tool.PatchDiffRequest},
 * an error message - {@link ErrorDiffRequest}.
 *
 * @see com.intellij.diff.DiffRequestFactory
 * @see SimpleDiffRequest
 */
public abstract class DiffRequest implements UserDataHolder {
  protected final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();

  public abstract @NlsContexts.DialogTitle @Nullable String getTitle();

  /*
   * Called when DiffRequest is shown
   *
   * Implementors may use this notification to add and remove listeners to avoid memory leaks.
   * DiffRequest could be shown multiple times, so implementors should count assignments
   *
   * @param isAssigned true means request processing started, false means processing has stopped.
   *                   Total number of calls with true should be same as for false
   */
  @RequiresEdt
  public void onAssigned(boolean isAssigned) {
  }

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

  /**
   * @see com.intellij.openapi.fileEditor.FileEditor#getFilesToRefresh()
   */
  public @NotNull @Unmodifiable List<VirtualFile> getFilesToRefresh() {
    return Collections.emptyList();
  }
}
