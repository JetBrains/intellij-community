// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class HighlightImportedElementsHandlerFactory extends HighlightUsagesHandlerFactoryBase {

  @Nullable
  @Override
  public HighlightUsagesHandlerBase<PsiMember> createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target) {
    if (!(target instanceof PsiKeyword) || !PsiKeyword.IMPORT.equals(target.getText())) {
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
    return new HighlightImportedElementsHandler(editor, file, target, (PsiImportStatementBase) parent);
  }
}
