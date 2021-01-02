// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

/**
 * @see NavigatableFileEditor
 */
public interface FileEditorNavigatable extends Navigatable {
  @NotNull
  VirtualFile getFile();

  default boolean isUseCurrentWindow() {
    return false;
  }
}
