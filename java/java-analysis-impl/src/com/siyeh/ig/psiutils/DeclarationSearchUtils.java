/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeclarationSearchUtils {

  private DeclarationSearchUtils() {}

  public static boolean variableNameResolvesToTarget(@NotNull String variableName, @NotNull PsiVariable target,
                                                     @NotNull PsiElement context) {
    final Project project = context.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiResolveHelper resolveHelper = psiFacade.getResolveHelper();
    final PsiVariable variable = resolveHelper.resolveAccessibleReferencedVariable(variableName, context);
    return target.equals(variable);
  }

  public static PsiExpression findDefinition(@NotNull PsiReferenceExpression referenceExpression,
                                             @Nullable PsiVariable variable) {
    if (variable == null) {
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable)) {
        return null;
      }
      variable = (PsiVariable)target;
    }
    final PsiCodeBlock block = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    if (block == null) {
      return null;
    }
    final PsiElement[] defs = DefUseUtil.getDefs(block, variable, referenceExpression);
    if (defs.length != 1) {
      return null;
    }
    final PsiElement def = defs[0];
    if (def instanceof PsiVariable) {
      final PsiVariable target = (PsiVariable)def;
      final PsiExpression initializer = target.getInitializer();
      return PsiUtil.skipParenthesizedExprDown(initializer);
    }
    else if (def instanceof PsiReferenceExpression) {
      final PsiElement parent = def.getParent();
      if (!(parent instanceof PsiAssignmentExpression assignmentExpression)) {
        return null;
      }
      if (assignmentExpression.getOperationTokenType() != JavaTokenType.EQ) {
        return null;
      }
      return PsiUtil.skipParenthesizedExprDown(assignmentExpression.getRExpression());
    }
    return null;
  }

  public static boolean isTooExpensiveToSearch(PsiNamedElement element, boolean zeroResult) {
    final String name = element.getName();
    if (name == null) {
      return true;
    }
    final ProgressManager progressManager = ProgressManager.getInstance();
    final PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(element.getProject());
    final SearchScope useScope = element.getUseScope();
    if (!(useScope instanceof GlobalSearchScope)) {
      return false;
    }
    final PsiSearchHelper.SearchCostResult cost =
      searchHelper.isCheapEnoughToSearch(name, (GlobalSearchScope)useScope, null, progressManager.getProgressIndicator());
    if (cost == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES) {
      return zeroResult;
    }
    return cost == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES;
  }

  public static PsiField findFirstFieldInDeclaration(PsiField field) {
    final PsiTypeElement typeElement = field.getTypeElement();
    if (typeElement == null) return field; // e.g. enum constant
    return (PsiField)typeElement.getParent();
  }

  public static PsiField findNextFieldInDeclaration(PsiField field) {
    final PsiField nextField = PsiTreeUtil.getNextSiblingOfType(field, PsiField.class);
    return nextField != null && field.getTypeElement() == nextField.getTypeElement() ? nextField : null;
  }
}