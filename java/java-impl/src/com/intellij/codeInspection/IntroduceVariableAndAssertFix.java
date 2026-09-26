// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.AddAssertNonNullFromTestFrameworksFix.Variant;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * Extracts an expression into a new local variable, asserts something about that variable, and uses the variable
 * in the place of the original expression.
 * <p>
 * Unlike {@link AddAssertStatementFix}, this fix is applicable to expressions that the dataflow analysis cannot represent
 * as a stable value (e.g., {@code arr[i]} or {@code map.get(key)}): asserting such an expression in-place does not
 * remove the warning, as the next evaluation of the same expression may produce another value.
 */
public final class IntroduceVariableAndAssertFix extends PsiUpdateModCommandQuickFix {
  private final @NotNull SmartPsiElementPointer<PsiExpression> myExpressionPointer;
  private final @NonNls @NotNull String myAssertionPrefix;
  private final @NonNls @NotNull String myAssertionSuffix;
  private final @Nls @NotNull String myName;

  private IntroduceVariableAndAssertFix(@NotNull PsiExpression expression,
                                        @NonNls @NotNull String assertionPrefix,
                                        @NonNls @NotNull String assertionSuffix,
                                        @Nls @NotNull String name) {
    myExpressionPointer = SmartPointerManager.getInstance(expression.getProject()).createSmartPsiElementPointer(expression);
    myAssertionPrefix = assertionPrefix;
    myAssertionSuffix = assertionSuffix;
    myName = name;
  }

  /**
   * @param expression expression to extract into a local variable
   * @param suffix     text to append to the variable name to get the assertion condition (e.g., {@code " != null"})
   * @return a new fix that adds an {@code assert} statement; null if the expression cannot be extracted into a local variable
   */
  public static @Nullable IntroduceVariableAndAssertFix create(@NotNull PsiExpression expression, @NotNull String suffix) {
    return create(expression, "assert ", suffix,
                  name -> JavaBundle.message("inspection.introduce.variable.and.assert.quickfix", name + suffix));
  }

  /**
   * @param expression expression to extract into a local variable
   * @param variant    test framework whose {@code assertNotNull} method should be called
   * @return a new fix that adds an {@code assertNotNull} call; null if the expression cannot be extracted into a local variable
   */
  public static @Nullable IntroduceVariableAndAssertFix create(@NotNull PsiExpression expression, @NotNull Variant variant) {
    return create(expression, variant.methodReference + "(", ")",
                  name -> JavaBundle.message("inspection.introduce.variable.and.testframework.assert.quickfix",
                                             variant.name, variant.replacement + "(" + name + ")"));
  }

  private static @Nullable IntroduceVariableAndAssertFix create(@NotNull PsiExpression expression,
                                                                @NonNls @NotNull String assertionPrefix,
                                                                @NonNls @NotNull String assertionSuffix,
                                                                @NotNull Function<@NotNull String, @Nls @NotNull String> presentation) {
    List<String> names = ExtractedVariableInfo.suggestNames(expression);
    if (names.isEmpty()) return null;
    return new IntroduceVariableAndAssertFix(expression, assertionPrefix, assertionSuffix, presentation.apply(names.getFirst()));
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("inspection.introduce.variable.and.assert.family");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiExpression expression = updater.getWritable(myExpressionPointer.getElement());
    if (expression == null) return;
    ExtractedVariableInfo extracted = ExtractedVariableInfo.extract(expression);
    if (extracted == null) return;

    @NonNls String assertionText = myAssertionPrefix + extracted.variable().getName() + myAssertionSuffix + ";";
    PsiStatement assertion = JavaPsiFacade.getElementFactory(project).createStatementFromText(assertionText, extracted.statement());
    PsiElement anchor = extracted.anchor();
    PsiElement addedAssertion = anchor.getParent().addBefore(assertion, anchor);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedAssertion);

    updater.rename(extracted.variable(), extracted.names());
  }
}
