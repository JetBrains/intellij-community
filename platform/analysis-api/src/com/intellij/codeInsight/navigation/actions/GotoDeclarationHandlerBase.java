// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public abstract class GotoDeclarationHandlerBase implements GotoDeclarationHandler {
  @Override
  public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
    final PsiElement target = getGotoDeclarationTarget(sourceElement, editor);
    return target != null ? new PsiElement[]{target} : null;
  }

  public abstract @Nullable PsiElement getGotoDeclarationTarget(@Nullable PsiElement sourceElement, Editor editor);
}
