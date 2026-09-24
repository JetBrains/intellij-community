// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.AddAssertNonNullFromTestFrameworksFix.Variant;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ReorderingUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
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
 * Unlike {@link AddAssertStatementFix}, this fix is applicable to expressions that dataflow analysis cannot represent
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
    PsiExpression stripped = deparenthesize(expression);
    PsiType type = getVariableType(stripped);
    if (type == null) return null;
    if (PsiUtil.isAccessedForWriting(expression)) return null;
    if (!CodeBlockSurrounder.canSurround(expression)) return null;
    if (ReorderingUtils.canExtract(ExpressionUtils.getTopLevelExpression(expression), expression) == ThreeState.NO) return null;
    String name = suggestNames(stripped, type).getFirst();
    return new IntroduceVariableAndAssertFix(expression, assertionPrefix, assertionSuffix, presentation.apply(name));
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
    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(expression);
    if (surrounder == null) return;
    CodeBlockSurrounder.SurroundResult result = surrounder.surround();
    PsiExpression extracted = result.getExpression();
    PsiExpression initializer = deparenthesize(extracted);
    PsiType type = getVariableType(initializer);
    if (type == null) return;
    PsiElement anchor = result.getSuppressionAwareAnchor();

    List<String> names = suggestNames(initializer, type);
    String name = names.getFirst();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiDeclarationStatement declaration = factory.createVariableDeclarationStatement(name, type, initializer, initializer);
    @NonNls String assertText = myAssertionPrefix + name + myAssertionSuffix + ";";
    PsiStatement assertStatement = factory.createStatementFromText(assertText, initializer);

    PsiElement parent = anchor.getParent();
    PsiDeclarationStatement added = (PsiDeclarationStatement)parent.addBefore(declaration, anchor);
    PsiElement addedAssertion = parent.addBefore(assertStatement, anchor);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedAssertion);
    // a reference never needs the parentheses the original expression could have required
    PsiExpression toReplace = extracted;
    while (toReplace.getParent() instanceof PsiParenthesizedExpression parentheses) {
      toReplace = parentheses;
    }
    toReplace.replace(factory.createExpressionFromText(name, toReplace));

    PsiLocalVariable variable = ObjectUtils.tryCast(added.getDeclaredElements()[0], PsiLocalVariable.class);
    if (variable != null) {
      updater.rename(variable, names);
    }
  }

  private static @NotNull PsiExpression deparenthesize(@NotNull PsiExpression expression) {
    PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(expression);
    return stripped == null ? expression : stripped;
  }

  private static @NotNull List<String> suggestNames(@NotNull PsiExpression expression, @NotNull PsiType type) {
    return new VariableNameGenerator(expression, VariableKind.LOCAL_VARIABLE).byExpression(expression).byType(type).generateAll(true);
  }

  private static @Nullable PsiType getVariableType(@NotNull PsiExpression expression) {
    PsiType type = GenericsUtil.getVariableTypeByExpressionType(expression.getType());
    if (type == null || PsiTypes.voidType().equals(type) || PsiTypes.nullType().equals(type)) return null;
    if (!PsiTypesUtil.isDenotableType(type, expression)) return null;
    // drop the type annotations: the variable is asserted right after its declaration,
    // so a copied '@Nullable' would contradict the assertion
    if (!type.hasAnnotations()) return type;
    return JavaPsiFacade.getElementFactory(expression.getProject()).createTypeFromText(type.getCanonicalText(false), expression);
  }
}
