// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

@ApiStatus.Experimental
public interface LightEditorInfo {

  @NotNull FileEditor getFileEditor();

  @NotNull VirtualFile getFile();

  /**
   * @return True if the document either is new and has never been saved or has been modified but not saved.
   * @see #isSaveRequired()
   */
  boolean isUnsaved();

  /**
   * @return True if the document exists only in memory and doesn't have a corresponding physical file.
   */
  boolean isNew();

  /**
   * @return The same value as {@link #isUnsaved()} for already saved but modified documents. For new documents which have never been
   * saved yet (exist only in memory), returns true only if document content is not empty.
   */
  boolean isSaveRequired();

  @Nullable Path getPreferredSavePath();

  void setPreferredSavePath(@Nullable Path preferredSavePath);
}
