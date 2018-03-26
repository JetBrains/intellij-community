// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.actions.WordBoundaryFilter;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class JavaWordBoundaryFilter extends WordBoundaryFilter {
  @Override
  public boolean isWordBoundary(@NotNull IElementType previousTokenType, @NotNull IElementType tokenType) {
    if (previousTokenType == JavaTokenType.GT && tokenType == JavaTokenType.EQ) return false;
    return super.isWordBoundary(previousTokenType, tokenType);
  }
}
