// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MethodMayBeSynchronizedInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("method.may.be.synchronized.problem.descriptor");
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    return new MethodMayBeSynchronizedQuickFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodMayBeSynchronizedVisitor();
  }

  private static class MethodMayBeSynchronizedQuickFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("method.may.be.synchronized.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiMethod method = (PsiMethod)startElement.getParent();
      final PsiCodeBlock methodBody = method.getBody();
      final PsiStatement statement = ControlFlowUtils.getOnlyStatementInBlock(methodBody);
      if (!(statement instanceof PsiSynchronizedStatement synchronizedStatement)) {
        return;
      }
      final PsiCodeBlock body = synchronizedStatement.getBody();
      if (body == null) {
        return;
      }
      final CommentTracker tracker = new CommentTracker();
      tracker.markUnchanged(body);
      tracker.grabComments(methodBody);
      final PsiCodeBlock newBlock = (PsiCodeBlock)methodBody.replace(body);
      PsiElement first = newBlock.getFirstBodyElement();
      if (first instanceof PsiWhiteSpace) first = first.getNextSibling();
      if (first != null) {
        tracker.insertCommentsBefore(first);
      }
      final PsiModifierList modifierList = method.getModifierList();
      modifierList.setModifierProperty(PsiModifier.SYNCHRONIZED, true);
    }
  }

  private static class MethodMayBeSynchronizedVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final PsiElement parent = statement.getParent();
      if (!(parent instanceof PsiCodeBlock)) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethod method)) {
        return;
      }
      if (!ControlFlowUtils.hasStatementCount(method.getBody(), 1)) {
        return;
      }
      final PsiExpression lockExpression = statement.getLockExpression();
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        if (!(lockExpression instanceof PsiClassObjectAccessExpression classExpression)) {
          return;
        }
        final PsiTypeElement typeElement = classExpression.getOperand();
        final PsiType type = typeElement.getType();
        if (!(type instanceof PsiClassType classType)) {
          return;
        }
        final PsiClass aClass = classType.resolve();
        final PsiClass containingClass = method.getContainingClass();
        if (aClass != containingClass) {
          return;
        }
      }
      else {
        if (!(lockExpression instanceof PsiThisExpression thisExpression)) {
          return;
        }
        final PsiJavaCodeReferenceElement qualifier = thisExpression.getQualifier();
        if (qualifier != null) {
          final PsiElement target = qualifier.resolve();
          final PsiClass containingClass = method.getContainingClass();
          if (containingClass == null || !containingClass.equals(target)) {
            return;
          }
        }
      }
      registerMethodError(method);
    }
  }
}