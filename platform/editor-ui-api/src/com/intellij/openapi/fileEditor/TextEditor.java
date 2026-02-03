// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public interface TextEditor extends NavigatableFileEditor {
  @NotNull Editor getEditor();

  @ApiStatus.Internal
  @ApiStatus.Experimental
  default boolean isEditorLoaded() {
    return true;
  }
}
