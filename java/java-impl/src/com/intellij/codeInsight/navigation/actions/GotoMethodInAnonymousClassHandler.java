// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.scope.util.PsiScopesUtil;
import org.jetbrains.annotations.Nullable;

public final class GotoMethodInAnonymousClassHandler extends GotoDeclarationHandlerBase {

  @Override
  public @Nullable PsiElement getGotoDeclarationTarget(@Nullable PsiElement elementAt, Editor editor) {
    if (elementAt instanceof PsiIdentifier) {
      PsiElement parent = elementAt.getParent();
      if (parent instanceof PsiReferenceExpression) {
        PsiElement gParent = parent.getParent();
        if (gParent instanceof PsiMethodCallExpression) {
          PsiExpression qualifierExpression = ((PsiMethodCallExpression)gParent).getMethodExpression().getQualifierExpression();
          if (qualifierExpression instanceof PsiReferenceExpression) {
            PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
            if (resolve instanceof PsiVariable && 
                ((PsiVariable)resolve).hasModifierProperty(PsiModifier.FINAL) && 
                ((PsiVariable)resolve).hasInitializer()) {
              final PsiExpression initializer = ((PsiVariable)resolve).getInitializer();
              if (initializer instanceof PsiNewExpression) {
                final PsiAnonymousClass anonymousClass = ((PsiNewExpression)initializer).getAnonymousClass();
                if (anonymousClass != null) {
                  return PsiScopesUtil.getOverridingMethod(anonymousClass, elementAt.getText());
                }
              }
            }
          }
        }
      }
    }
    return null;
  }
}
