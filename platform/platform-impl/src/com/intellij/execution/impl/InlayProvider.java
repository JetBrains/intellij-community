// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface InlayProvider {
  default EditorCustomElementRenderer createInlayRenderer(Editor editor) {
    throw new UnsupportedOperationException();
  }

  default @Nullable Inlay<?> createInlay(@NotNull Editor editor, int offset) {
    return editor.getInlayModel().addInlineElement(offset, createInlayRenderer(editor));
  }
}
