// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SplitDeclarationAction extends PsiUpdateModCommandAction<PsiDeclarationStatement> implements DumbAware {
  public SplitDeclarationAction() {
    super(PsiDeclarationStatement.class);
  }
  
  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.split.declaration.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiDeclarationStatement element) {
    if (PsiTreeUtil.getParentOfType(context.findLeaf(), PsiDeclarationStatement.class, false, PsiClass.class, PsiCodeBlock.class)
        != element) {
      return null;
    }
    return isAvailableOnDeclarationStatement(element) ?
           Presentation.of(JavaBundle.message("intention.split.declaration.assignment.text")) : null;
  }

  private boolean isAvailableOnDeclarationStatement(PsiDeclarationStatement decl) {
    PsiElement[] declaredElements = decl.getDeclaredElements();
    if (declaredElements.length != 1) return false;
    if (!(declaredElements[0] instanceof PsiLocalVariable var)) return false;
    if (var.getInitializer() == null) return false;
    if (var.getTypeElement().isInferredType() && !PsiTypesUtil.isDenotableType(var.getType(), var)) {
      return false;
    }
    PsiElement parent = decl.getParent();
    if (parent instanceof PsiForStatement) {
      String varName = var.getName();

      parent = parent.getNextSibling();
      while (parent != null) {
        Ref<Boolean> conflictFound = new Ref<>(false);
        parent.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitClass(@NotNull PsiClass aClass) { }

          @Override
          public void visitVariable(@NotNull PsiVariable variable) {
            super.visitVariable(variable);
            if (varName.equals(variable.getName())) {
              conflictFound.set(true);
              stopWalking();
            }
          }
        });
        if (conflictFound.get()) {
          return false;
        }
        parent = parent.getNextSibling();
      }
    }
    return true;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiDeclarationStatement decl, @NotNull ModPsiUpdater updater) {
    ExpressionUtils.splitDeclaration(decl, context.project());
  }
}
