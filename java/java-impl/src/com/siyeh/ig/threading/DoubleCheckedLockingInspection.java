/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.IntroduceHolderFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DoubleCheckedLockingInspection extends BaseInspection {

  /**
   * Preserved for serialization compatibility
   */
  @SuppressWarnings("unused") public boolean ignoreOnVolatileVariables = false;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("double.checked.locking.problem.descriptor");
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    final PsiIfStatement innerIf = (PsiIfStatement)infos[1];
    final PsiIfStatement outerIf = (PsiIfStatement)infos[2];
    final List<LocalQuickFix> fixes = new SmartList<>();
    final IntroduceHolderFix fix = IntroduceHolderFix.createFix(field, innerIf);
    if (fix != null && outerIf.getElseBranch() == null) {
      fixes.add(fix);
    }
    fixes.add(new DoubleCheckedLockingFix(field));
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  private static final class DoubleCheckedLockingFix extends PsiUpdateModCommandQuickFix {

    private final String myFieldName;

    private DoubleCheckedLockingFix(PsiField field) {
      myFieldName = field.getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("double.checked.locking.quickfix", myFieldName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("double.checked.locking.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiIfStatement ifStatement)) {
        return;
      }
      final PsiExpression condition = ifStatement.getCondition();
      if (condition == null) {
        return;
      }
      final PsiField field = findCheckedField(condition);
      if (field == null) {
        return;
      }
      final PsiModifierList modifierList = field.getModifierList();
      if (modifierList == null) {
        return;
      }
      modifierList.setModifierProperty(PsiModifier.VOLATILE, true);
    }
  }

  @Nullable
  private static PsiField findCheckedField(PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression referenceExpression) {
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiField)) {
        return null;
      }
      return (PsiField)target;
    }
    else if (expression instanceof PsiBinaryExpression binaryExpression) {
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!ComparisonUtils.isComparisonOperation(tokenType)) {
        return null;
      }
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      final PsiField field = findCheckedField(lhs);
      if (field != null) {
        return field;
      }
      return findCheckedField(rhs);
    }
    else if (expression instanceof PsiPrefixExpression prefixExpression) {
      final IElementType tokenType =
        prefixExpression.getOperationTokenType();
      if (!JavaTokenType.EXCL.equals(tokenType)) {
        return null;
      }
      return findCheckedField(prefixExpression.getOperand());
    }
    else {
      return null;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DoubleCheckedLockingVisitor();
  }

  private static class DoubleCheckedLockingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiExpression outerCondition = statement.getCondition();
      if (outerCondition == null || SideEffectChecker.mayHaveSideEffects(outerCondition)) {
        return;
      }
      final PsiIfStatement innerIf = IntroduceHolderFix.getDoubleCheckedLockingInnerIf(statement);
      if (innerIf == null) {
        return;
      }
      final PsiExpression innerCondition = innerIf.getCondition();
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(innerCondition, outerCondition)) {
        return;
      }
      final PsiField field = findCheckedField(innerCondition);
      if (field == null || field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (field.hasModifierProperty(PsiModifier.VOLATILE) && PsiUtil.isLanguageLevel5OrHigher(statement)) {
        return;
      }
      registerStatementError(statement, field, innerIf, statement);
    }
  }
}