// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Extracts an expression into a new local variable and replaces the dereference of that variable with a conditional
 * expression that yields a default value when the variable is null.
 * <p>
 * Unlike {@link ReplaceWithTernaryOperatorFix}, this fix is applicable to expressions that the dataflow analysis cannot
 * represent as a stable value (e.g., {@code arr[i]} or {@code map.get(key)}), as well as to expressions with side effects:
 * the generated condition tests the variable, so the original expression is neither re-evaluated nor left unchecked.
 */
public final class IntroduceVariableAndReplaceWithTernaryFix extends PsiUpdateModCommandQuickFix {
  private final @NotNull SmartPsiElementPointer<PsiExpression> myExpressionPointer;
  private final @NotNull String myVariableName;

  private IntroduceVariableAndReplaceWithTernaryFix(@NotNull PsiExpression expression, @NotNull String variableName) {
    myExpressionPointer = SmartPointerManager.getInstance(expression.getProject()).createSmartPsiElementPointer(expression);
    myVariableName = variableName;
  }

  /**
   * @param qualifier expression to extract into a local variable and to check for null
   * @return a new fix, or null if the expression cannot be extracted into a local variable
   */
  public static @Nullable IntroduceVariableAndReplaceWithTernaryFix create(@NotNull PsiExpression qualifier) {
    // if nothing is dereferenced, the conditional expression would simply yield the variable back
    if (getOutermostDereference(qualifier) == qualifier) return null;
    List<String> names = ExtractedVariableInfo.suggestNames(qualifier);
    if (names.isEmpty()) return null;
    return new IntroduceVariableAndReplaceWithTernaryFix(qualifier, names.getFirst());
  }

  @Override
  public @NotNull String getName() {
    return JavaBundle.message("inspection.introduce.variable.and.replace.ternary.quickfix", myVariableName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("inspection.introduce.variable.and.replace.ternary.family");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiExpression qualifier = updater.getWritable(myExpressionPointer.getElement());
    if (qualifier == null) return;
    ExtractedVariableInfo extracted = ExtractedVariableInfo.extract(qualifier);
    if (extracted == null) return;

    PsiExpression dereference = getOutermostDereference(extracted.reference());
    ReplaceWithTernaryOperatorFix.replaceWithConditionalExpression(
      project, extracted.variable().getName() + "!=null", dereference, ReplaceWithTernaryOperatorFix.suggestDefaultValue(dereference));

    updater.rename(extracted.variable(), extracted.names());
  }

  /**
   * @param expression expression that may be dereferenced
   * @return the outermost expression that dereferences the given one, and thus produces a value only if the given
   * expression is not null; the expression itself if it's not dereferenced
   */
  private static @NotNull PsiExpression getOutermostDereference(@NotNull PsiExpression expression) {
    PsiExpression result = expression;
    while (true) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(result.getParent());
      if (parent instanceof PsiReferenceExpression reference && PsiUtil.skipParenthesizedExprDown(reference.getQualifierExpression()) == result ||
          parent instanceof PsiMethodCallExpression call && call.getMethodExpression() == result ||
          parent instanceof PsiArrayAccessExpression access && PsiUtil.skipParenthesizedExprDown(access.getArrayExpression()) == result ||
          parent instanceof PsiNewExpression creation && PsiUtil.skipParenthesizedExprDown(creation.getQualifier()) == result) {
        result = (PsiExpression)parent;
      }
      else {
        return result;
      }
    }
  }
}
