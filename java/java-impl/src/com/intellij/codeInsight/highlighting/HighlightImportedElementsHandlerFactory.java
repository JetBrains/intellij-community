// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class HighlightImportedElementsHandlerFactory extends HighlightUsagesHandlerFactoryBase {

  @Override
  public @Nullable HighlightUsagesHandlerBase<PsiMember> createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile psiFile, @NotNull PsiElement target) {
    if (!(target instanceof PsiKeyword) || !JavaKeywords.IMPORT.equals(target.getText())) {
      return null;
    }
    final PsiElement parent = target.getParent();
    if (!(parent instanceof PsiImportStatementBase)) {
      return null;
    }
    final PsiElement grand = parent.getParent();
    if (!(grand instanceof PsiImportList)) {
      return null;
    }
    return new HighlightImportedElementsHandler(editor, psiFile, target, (PsiImportStatementBase) parent);
  }
}
