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

  boolean isUnsaved();

  boolean isNew();

  @Nullable Path getPreferredSavePath();

  void setPreferredSavePath(@Nullable Path preferredSavePath);
}
