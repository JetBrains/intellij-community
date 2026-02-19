// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.contents;

import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.pom.Navigatable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.Nullable;

/**
 * Represents some data that probably can be compared with some other.
 *
 * @see com.intellij.diff.requests.ContentDiffRequest
 * @see com.intellij.diff.DiffContentFactory
 * @see DocumentContent
 * @see FileContent
 * @see DirectoryContent
 */
public interface DiffContent extends UserDataHolder {
  @Nullable
  FileType getContentType();

  /**
   * Provides a way to open related content in editor
   */
  default @Nullable Navigatable getNavigatable() { return null; }

  /**
   * @see DiffRequest#onAssigned(boolean)
   */
  @RequiresEdt
  default void onAssigned(boolean isAssigned) { }

  /**
   * @deprecated isn't called by the platform anymore
   */
  @Deprecated(forRemoval = true)
  default @Nullable OpenFileDescriptor getOpenFileDescriptor() {
    return ObjectUtils.tryCast(getNavigatable(), OpenFileDescriptor.class);
  }
}
