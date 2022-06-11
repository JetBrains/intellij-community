// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public class SplitDeclarationAction extends PsiElementBaseIntentionAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.split.declaration.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (element instanceof PsiCompiledElement) return false;
    if (!canModify(element)) return false;
    if (!element.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return false;

    final PsiDeclarationStatement
      context = PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class, false, PsiClass.class, PsiCodeBlock.class);
    return context != null && isAvailableOnDeclarationStatement(context);
  }

  private boolean isAvailableOnDeclarationStatement(PsiDeclarationStatement decl) {
    PsiElement[] declaredElements = decl.getDeclaredElements();
    if (declaredElements.length != 1) return false;
    if (!(declaredElements[0] instanceof PsiLocalVariable)) return false;
    PsiLocalVariable var = (PsiLocalVariable)declaredElements[0];
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
    setText(JavaBundle.message("intention.split.declaration.assignment.text"));
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiDeclarationStatement decl = PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class);

    if (decl != null) {
      ExpressionUtils.splitDeclaration(decl, project);
    }
  }
}
