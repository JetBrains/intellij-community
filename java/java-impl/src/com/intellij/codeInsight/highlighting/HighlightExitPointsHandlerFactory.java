// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiKeyword;
import org.jetbrains.annotations.NotNull;


public class HighlightExitPointsHandlerFactory extends HighlightUsagesHandlerFactoryBase {
  @Override
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target) {
    if (target instanceof PsiKeyword) {
      if (PsiKeyword.RETURN.equals(target.getText()) || PsiKeyword.THROW.equals(target.getText())) {
        return new HighlightExitPointsHandler(editor, file, target);
      }
      if (PsiKeyword.CONTINUE.equals(target.getText()) || PsiKeyword.BREAK.equals(target.getText()) || PsiKeyword.YIELD.equals(target.getText())) {
        return new HighlightBreakOutsHandler(editor, file, target);
      }
    }
    return null;
  }
}