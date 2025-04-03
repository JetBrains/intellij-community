// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;


public final class HighlightOverridingMethodsHandlerFactory extends HighlightUsagesHandlerFactoryBase {
  @Override
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target) {
    if (target instanceof PsiKeyword && (JavaKeywords.EXTENDS.equals(target.getText()) || JavaKeywords.IMPLEMENTS.equals(target.getText()))) {
      PsiElement parent = target.getParent();
      if (!(parent instanceof PsiReferenceList)) return null;
      PsiElement grand = parent.getParent();
      if (!(grand instanceof PsiClass)) return null;
      return new HighlightOverridingMethodsHandler(editor, file, target, (PsiClass) grand);
    }
    return null;
  }
}
