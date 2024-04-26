/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteUnnecessaryStatementFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class UnnecessaryReturnInspection extends BaseInspection implements CleanupLocalInspectionTool {
  @SuppressWarnings("PublicField")
  public boolean ignoreInThenBranch = false;

  @Override
  @NotNull
  public String getID() {
    return "UnnecessaryReturnStatement";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    if (((Boolean)infos[0]).booleanValue()) {
      return InspectionGadgetsBundle.message("unnecessary.return.constructor.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("unnecessary.return.problem.descriptor");
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreInThenBranch", InspectionGadgetsBundle.message("unnecessary.return.option")));
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new DeleteUnnecessaryStatementFix("return");
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return !FileTypeUtils.isInServerPageFile(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryReturnVisitor();
  }

  private class UnnecessaryReturnVisitor extends BaseInspectionVisitor {
    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      final Ref<Boolean> constructorRef = Ref.create();
      if (isReturnRedundant(statement, ignoreInThenBranch, true, constructorRef)) {
        registerError(statement.getFirstChild(), constructorRef.get());
      }
    }

  }

  public static boolean isReturnRedundant(@NotNull PsiReturnStatement statement,
                                          boolean ignoreInThenBranch,
                                          boolean checkReturnType,
                                          @Nullable Ref<? super Boolean> isInConstructorRef) {
    if (statement.getReturnValue() != null) {
      return false;
    }
    final PsiElement methodParent = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class);
    PsiCodeBlock codeBlock = null;
    if (methodParent instanceof PsiMethod method) {
      codeBlock = method.getBody();
      if (isInConstructorRef != null) {
        isInConstructorRef.set(method.isConstructor());
      }
      if (checkReturnType && !method.isConstructor() && !PsiTypes.voidType().equals(method.getReturnType())) {
        return false;
      }
    }
    else if (methodParent instanceof PsiLambdaExpression lambdaExpression) {
      if (isInConstructorRef != null) {
        isInConstructorRef.set(false);
      }
      if (checkReturnType && !PsiTypes.voidType().equals(LambdaUtil.getFunctionalInterfaceReturnType(lambdaExpression))) {
        return false;
      }
      final PsiElement lambdaBody = lambdaExpression.getBody();
      if (lambdaBody instanceof PsiCodeBlock) {
        codeBlock = (PsiCodeBlock)lambdaBody;
      }
    }
    else {
      return false;
    }
    if (codeBlock == null) {
      return false;
    }
    if (!ControlFlowUtils.blockCompletesWithStatement(codeBlock, statement) || ControlFlowUtils.isInFinallyBlock(statement, null)) {
      return false;
    }
    if (ignoreInThenBranch && isInThenBranch(statement)) {
      return false;
    }
    return true;
  }

  static boolean isInThenBranch(PsiStatement statement) {
    final PsiIfStatement ifStatement =
      PsiTreeUtil.getParentOfType(statement, PsiIfStatement.class, true, PsiMethod.class, PsiLambdaExpression.class);
    if (ifStatement == null) {
      return false;
    }
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    return elseBranch != null && !PsiTreeUtil.isAncestor(elseBranch, statement, true);
  }
}