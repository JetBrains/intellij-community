/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.requests;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @see com.intellij.diff.DiffRequestFactory
 * @see SimpleDiffRequest
 */
public abstract class DiffRequest implements UserDataHolder {
  protected final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();

  @NlsContexts.DialogTitle
  @Nullable
  public abstract String getTitle();

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
  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myUserDataHolder.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myUserDataHolder.putUserData(key, value);
  }

  /**
   * @see com.intellij.openapi.fileEditor.FileEditor#getFilesToRefresh()
   */
  @NotNull
  public List<VirtualFile> getFilesToRefresh() {
    return Collections.emptyList();
  }
}
