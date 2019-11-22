// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesScheme;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class CustomHighlightInfoHolder extends HighlightInfoHolder {
  private final EditorColorsScheme myCustomColorsScheme;

  CustomHighlightInfoHolder(@NotNull PsiFile contextFile,
                            @Nullable EditorColorsScheme customColorsScheme,
                            @NotNull HighlightInfoFilter... filters) {
    super(contextFile, filters);
    myCustomColorsScheme = customColorsScheme;
  }

  @Override
  @NotNull
  public TextAttributesScheme getColorsScheme() {
    if (myCustomColorsScheme != null) {
      return myCustomColorsScheme;
    }
    return EditorColorsManager.getInstance().getGlobalScheme();
  }
}
