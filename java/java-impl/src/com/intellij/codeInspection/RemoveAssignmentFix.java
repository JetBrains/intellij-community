/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.quickfix.RemoveUnusedVariableUtil;
import com.intellij.codeInsight.editorActions.DeclarationJoinLinesHandler;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoveAssignmentFix extends RemoveInitializerFix {
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("inspection.unused.assignment.remove.assignment.quickfix");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    PsiAssignmentExpression parentExpr = getAssignment(descriptor);
    if (parentExpr == null) return;
    PsiElement gParentExpr = parentExpr.getParent();
    PsiExpression initializer = getInitializer(parentExpr);
    if (initializer == null) return;
    if (mayBeFixedWithoutSideEffect(gParentExpr)) {
      if (!FileModificationService.getInstance().prepareFileForWrite(gParentExpr.getContainingFile())) return;
      WriteAction.run(() -> {
        if (gParentExpr instanceof PsiParenthesizedExpression) {
          gParentExpr.replace(initializer);
        } else {
          parentExpr.replace(initializer);
        }
      });
      return;
    }

    PsiElement resolve = resolveExpression(element, parentExpr);
    if (!(resolve instanceof PsiVariable)) return;

    sideEffectAwareRemove(project, initializer, parentExpr, (PsiVariable)resolve);
  }

  PsiAssignmentExpression getAssignment(@NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElement parent = element instanceof PsiReferenceExpression ? element.getParent() : element;
    return ObjectUtils.tryCast(parent, PsiAssignmentExpression.class);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    final PsiElement element = previewDescriptor.getPsiElement();
    PsiAssignmentExpression parentExpr = getAssignment(previewDescriptor);
    if (parentExpr == null) return IntentionPreviewInfo.EMPTY;
    PsiElement gParentExpr = parentExpr.getParent();
    PsiExpression initializer = getInitializer(parentExpr);
    if (initializer == null) return IntentionPreviewInfo.EMPTY;
    if (mayBeFixedWithoutSideEffect(gParentExpr)) {
      PsiElement target = gParentExpr instanceof PsiParenthesizedExpression ? gParentExpr : parentExpr;
      target.replace(initializer);
      return IntentionPreviewInfo.DIFF;
    }
    PsiVariable resolve = ObjectUtils.tryCast(resolveExpression(element, parentExpr), PsiVariable.class);
    if (resolve == null) return IntentionPreviewInfo.EMPTY;

    RemoveUnusedVariableUtil.RemoveMode res = RemoveUnusedVariableUtil.getModeForPreview(initializer, resolve);
    doRemove(project, initializer, parentExpr, resolve, resolve.getParent(), res);
    return IntentionPreviewInfo.DIFF;
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

  private static boolean mayBeFixedWithoutSideEffect(@NotNull PsiElement expr) {
    return expr instanceof PsiExpression || expr instanceof PsiExpressionList || expr instanceof PsiReturnStatement
            || expr instanceof PsiLocalVariable;
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
