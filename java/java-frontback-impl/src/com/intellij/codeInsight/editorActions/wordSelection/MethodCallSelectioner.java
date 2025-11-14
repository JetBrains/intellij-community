// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class MethodCallSelectioner implements ExtendWordSelectionHandler {

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiMethodCallExpression;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)e;
    PsiElement referenceNameElement = methodCall.getMethodExpression().getReferenceNameElement();
    if (referenceNameElement == null) {
      return null;
    }
    else {
      return List.of(new TextRange(referenceNameElement.getTextRange().getStartOffset(), methodCall.getTextRange().getEndOffset()),
                     methodCall.getTextRange());
    }
  }
}
