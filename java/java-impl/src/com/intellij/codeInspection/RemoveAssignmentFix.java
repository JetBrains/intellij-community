// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInsight.editorActions.DeclarationJoinLinesHandler;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RemoveAssignmentFix extends ModCommandQuickFix {
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("inspection.unused.assignment.remove.assignment.quickfix");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    PsiAssignmentExpression parentExpr = getAssignment(descriptor);
    if (parentExpr == null) return ModCommand.nop();
    if (!ExpressionUtils.isVoidContext(parentExpr)) {
      return ModCommand.psiUpdate(parentExpr, p -> {
        PsiExpression initializer = getInitializer(p);
        if (initializer == null) return;
        PsiElement gp = p.getParent();
        if (gp instanceof PsiParenthesizedExpression) {
          gp.replace(initializer);
        } else {
          p.replace(initializer);
        }
      });
    }

    PsiExpression initializer = parentExpr.getRExpression();
    if (initializer == null) return ModCommand.nop();
    PsiElement resolve = resolveExpression(element, parentExpr);
    if (!(resolve instanceof PsiVariable)) return ModCommand.nop();
    List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(initializer);
    List<ModCommandAction> subActions;
    if (!sideEffects.isEmpty()) {
      subActions = List.of(new RemoveInitializerFix.SideEffectAwareRemove(initializer),
                           new DeleteElementFix(parentExpr, JavaBundle.message("delete.assignment.completely")));
    }
    else {
      subActions = List.of(new DeleteElementFix(parentExpr));
    }
    return ModCommand.chooseAction(JavaBundle.message("inspection.unused.assignment.remove.assignment.quickfix.title"), subActions);
  }

  PsiAssignmentExpression getAssignment(@NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElement parent = element instanceof PsiReferenceExpression ? element.getParent() : element;
    return ObjectUtils.tryCast(parent, PsiAssignmentExpression.class);
  }

  @Nullable
  private static PsiExpression getInitializer(@NotNull PsiAssignmentExpression assignmentExpr) {
    final IElementType operationSign = assignmentExpr.getOperationTokenType();
    PsiExpression result = assignmentExpr.getRExpression();
    if (JavaTokenType.EQ != operationSign && result != null) {
      result = DeclarationJoinLinesHandler.getInitializerExpression(assignmentExpr.getLExpression(), assignmentExpr);
    }
    return result;
  }

  @Nullable
  private static PsiElement resolveExpression(@NotNull PsiElement expr, @NotNull PsiAssignmentExpression parentExpr) {
    PsiElement result = null;
    if (expr instanceof PsiReferenceExpression) {
      result = ((PsiReferenceExpression)expr).resolve();
    } else {
      final PsiExpression lExpr = PsiUtil.deparenthesizeExpression(parentExpr.getLExpression());
      if (lExpr instanceof PsiReferenceExpression) {
        result = ((PsiReferenceExpression)lExpr).resolve();
      }
    }
    return result;
  }
}
