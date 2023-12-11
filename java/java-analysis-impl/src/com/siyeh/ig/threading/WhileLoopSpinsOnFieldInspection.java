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
package com.siyeh.ig.threading;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class WhileLoopSpinsOnFieldInspection extends BaseInspection {
  private static final CallMatcher THREAD_ON_SPIN_WAIT = CallMatcher.staticCall("java.lang.Thread", "onSpinWait");

  @SuppressWarnings({"PublicField", "SpellCheckingInspection"})
  public boolean ignoreNonEmtpyLoops = true;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("while.loop.spins.on.field.problem.descriptor");
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new SpinLoopFix((PsiField)infos[0], (boolean)infos[1]);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreNonEmtpyLoops", InspectionGadgetsBundle.message("while.loop.spins.on.field.ignore.non.empty.loops.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new WhileLoopSpinsOnFieldVisitor();
  }

  private class WhileLoopSpinsOnFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      final PsiStatement body = statement.getBody();
      boolean empty = ControlFlowUtils.statementIsEmpty(body);
      if (ignoreNonEmtpyLoops && !empty) {
        PsiExpressionStatement onlyExpr = ObjectUtils.tryCast(ControlFlowUtils.stripBraces(body), PsiExpressionStatement.class);
        if (onlyExpr == null || !THREAD_ON_SPIN_WAIT.matches(onlyExpr.getExpression())) return;
      }
      final PsiExpression condition = statement.getCondition();
      final PsiField field = getFieldIfSimpleFieldComparison(condition);
      if (field == null) return;
      if (body != null && (VariableAccessUtils.variableIsAssigned(field, body) || containsCall(body, ThreadingUtils::isWaitCall))) {
        return;
      }
      boolean java9 = PsiUtil.isLanguageLevel9OrHigher(field);
      boolean shouldAddSpinWait = java9 && empty && !containsCall(body, THREAD_ON_SPIN_WAIT);
      if (field.hasModifierProperty(PsiModifier.VOLATILE) && !shouldAddSpinWait) {
        return;
      }
      registerStatementError(statement, field, shouldAddSpinWait);
    }

    private boolean containsCall(@Nullable PsiElement element, Predicate<? super PsiMethodCallExpression> predicate) {
      if(element == null) return false;
      final boolean[] result = new boolean[1];
      element.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          if (predicate.test(expression)) {
            result[0] = true;
            stopWalking();
          }
        }
      });
      return result[0];
    }
  }

  @Nullable
  private static PsiField getFieldIfSimpleFieldComparison(PsiExpression condition) {
    condition = PsiUtil.deparenthesizeExpression(condition);
    if (condition == null) {
      return null;
    }
    final PsiField field = getFieldIfSimpleFieldAccess(condition);
    if (field != null) {
      return field;
    }
    if (condition instanceof PsiPrefixExpression prefixExpression) {
      IElementType type = prefixExpression.getOperationTokenType();
      if(!type.equals(JavaTokenType.PLUSPLUS) && !type.equals(JavaTokenType.MINUSMINUS)) {
        final PsiExpression operand = prefixExpression.getOperand();
        return getFieldIfSimpleFieldComparison(operand);
      }
    }
    if (condition instanceof PsiBinaryExpression binaryExpression) {
      final PsiExpression lOperand = binaryExpression.getLOperand();
      final PsiExpression rOperand = binaryExpression.getROperand();
      if (ExpressionUtils.isLiteral(rOperand)) {
        return getFieldIfSimpleFieldComparison(lOperand);
      }
      else if (ExpressionUtils.isLiteral(lOperand)) {
        return getFieldIfSimpleFieldComparison(rOperand);
      }
      else {
        return null;
      }
    }
    return null;
  }

  @Nullable
  private static PsiField getFieldIfSimpleFieldAccess(PsiExpression expression) {
    expression = PsiUtil.deparenthesizeExpression(expression);
    if (expression == null) {
      return null;
    }
    if (!(expression instanceof PsiReferenceExpression reference)) {
      return null;
    }
    final PsiExpression qualifierExpression = reference.getQualifierExpression();
    if (qualifierExpression != null) {
      return null;
    }
    final PsiElement referent = reference.resolve();
    if (!(referent instanceof PsiField field)) {
      return null;
    }
    if (field.hasModifierProperty(PsiModifier.VOLATILE) && !PsiUtil.isLanguageLevel9OrHigher(field)) {
      return null;
    }
    else {
      return field;
    }
  }

  private static class SpinLoopFix extends ModCommandQuickFix {
    private final String myFieldName;
    private final boolean myAddOnSpinWait;
    private final boolean myAddVolatile;

    SpinLoopFix(PsiField field, boolean addOnSpinWait) {
      myFieldName = field.getName();
      myAddOnSpinWait = addOnSpinWait;
      myAddVolatile = !field.hasModifierProperty(PsiModifier.VOLATILE);
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      if(myAddOnSpinWait && myAddVolatile) {
        return InspectionGadgetsBundle.message("while.loop.spins.on.field.fix.volatile.spinwait", myFieldName);
      }
      if(myAddOnSpinWait) {
        return InspectionGadgetsBundle.message("while.loop.spins.on.field.fix.spinwait");
      }
      return InspectionGadgetsBundle.message("while.loop.spins.on.field.fix.volatile", myFieldName);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("while.loop.spins.on.field.fix.family.name");
    }

    @Override
    public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      var element = descriptor.getStartElement();
      return ModCommand.psiUpdate(ActionContext.from(descriptor), updater -> {
        PsiModifierList modifierList = null;
        if (myAddVolatile) {
          modifierList = updater.getWritable(getFieldModifierList(element));
        }
        if (myAddOnSpinWait) {
          addOnSpinWait(updater.getWritable(element));
        }
        if (modifierList != null) {
          modifierList.setModifierProperty(PsiModifier.VOLATILE, true);
        }
      });
    }

    private static void addOnSpinWait(PsiElement element) {
      PsiLoopStatement loop = PsiTreeUtil.getParentOfType(element, PsiLoopStatement.class);
      if (loop == null) return;
      PsiStatement body = loop.getBody();
      if (body == null) return;
      PsiStatement spinCall =
        JavaPsiFacade.getElementFactory(element.getProject()).createStatementFromText("java.lang.Thread.onSpinWait();", element);
      if (body instanceof PsiBlockStatement) {
        PsiCodeBlock block = ((PsiBlockStatement)body).getCodeBlock();
        block.addAfter(spinCall, null);
      } else {
        BlockUtils.addBefore(body, spinCall);
      }
      CodeStyleManager.getInstance(element.getProject()).reformat(loop);
    }

    @Nullable
    private static PsiModifierList getFieldModifierList(PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiWhileStatement whileStatement)) return null;
      PsiExpression condition = whileStatement.getCondition();
      PsiField field = getFieldIfSimpleFieldComparison(condition);
      if (field == null) return null;
      return field.getModifierList();
    }
  }
}