// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiKeyword;
import org.jetbrains.annotations.NotNull;


public final class HighlightExitPointsHandlerFactory extends HighlightUsagesHandlerFactoryBase implements DumbAware {
  @Override
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile psiFile, @NotNull PsiElement target) {
    if (target instanceof PsiKeyword) {
      if (JavaKeywords.RETURN.equals(target.getText()) || JavaKeywords.THROW.equals(target.getText())) {
        return new HighlightExitPointsHandler(editor, psiFile, target);
      }
      if (JavaKeywords.CONTINUE.equals(target.getText()) || JavaKeywords.BREAK.equals(target.getText()) || JavaKeywords.YIELD.equals(target.getText())) {
        return new HighlightBreakOutsHandler(editor, psiFile, target);
      }
    }
    return null;
  }
}