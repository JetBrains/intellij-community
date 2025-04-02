// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.java.syntax.parser.PsiKeywords;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiKeyword;
import org.jetbrains.annotations.NotNull;


public final class HighlightExitPointsHandlerFactory extends HighlightUsagesHandlerFactoryBase implements DumbAware {
  @Override
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target) {
    if (target instanceof PsiKeyword) {
      if (PsiKeywords.RETURN.equals(target.getText()) || PsiKeywords.THROW.equals(target.getText())) {
        return new HighlightExitPointsHandler(editor, file, target);
      }
      if (PsiKeywords.CONTINUE.equals(target.getText()) || PsiKeywords.BREAK.equals(target.getText()) || PsiKeywords.YIELD.equals(target.getText())) {
        return new HighlightBreakOutsHandler(editor, file, target);
      }
    }
    return null;
  }
}