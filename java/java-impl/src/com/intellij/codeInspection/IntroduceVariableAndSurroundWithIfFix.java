// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.generation.surroundWith.JavaWithIfSurrounder;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.siyeh.ipp.trivialif.MergeIfAndIntention;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Extracts an expression into a new local variable, uses the variable in the place of the original expression,
 * and surrounds the statement with an {@code if} that checks the variable.
 * <p>
 * Unlike {@link SurroundWithIfFix}, this fix is applicable to expressions that the dataflow analysis cannot represent
 * as a stable value (e.g., {@code arr[i]} or {@code map.get(key)}), as well as to expressions with side effects:
 * the generated condition tests the variable, so the original expression is neither re-evaluated nor left unchecked.
 */
public final class IntroduceVariableAndSurroundWithIfFix extends PsiUpdateModCommandQuickFix {
  private final @NotNull SmartPsiElementPointer<PsiExpression> myExpressionPointer;
  private final @NotNull String mySuffix;
  private final @NotNull String myVariableName;

  private IntroduceVariableAndSurroundWithIfFix(@NotNull PsiExpression expression,
                                                @NotNull String suffix,
                                                @NotNull String variableName) {
    myExpressionPointer = SmartPointerManager.getInstance(expression.getProject()).createSmartPsiElementPointer(expression);
    mySuffix = suffix;
    myVariableName = variableName;
  }

  /**
   * @param expression expression to extract into a local variable
   * @param suffix     text to append to the variable name to get the condition of the generated {@code if}
   *                   (e.g., {@code " != null"})
   * @return a new fix, or null if the expression cannot be extracted into a local variable
   */
  public static @Nullable IntroduceVariableAndSurroundWithIfFix create(@NotNull PsiExpression expression, @NotNull String suffix) {
    List<String> names = ExtractedVariableInfo.suggestNames(expression);
    if (names.isEmpty()) return null;
    return new IntroduceVariableAndSurroundWithIfFix(expression, suffix, names.getFirst());
  }

  @Override
  public @NotNull String getName() {
    return JavaBundle.message("inspection.introduce.variable.and.surround.if.quickfix", myVariableName, mySuffix);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("inspection.introduce.variable.and.surround.if.family");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiExpression expression = updater.getWritable(myExpressionPointer.getElement());
    if (expression == null) return;
    ExtractedVariableInfo extracted = ExtractedVariableInfo.extract(expression);
    if (extracted == null) return;

    String condition = extracted.variable().getName() + mySuffix;
    PsiIfStatement ifStatement = new JavaWithIfSurrounder()
      .surroundStatements(project, extracted.statement().getParent(), extracted.statementWithSuppression(), condition);
    if (ifStatement == null) return;
    new MergeIfAndIntention().invoke(ifStatement.getFirstChild());

    updater.rename(extracted.variable(), extracted.names());
  }
}
