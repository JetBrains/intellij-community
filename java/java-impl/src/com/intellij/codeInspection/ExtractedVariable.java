// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

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
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ReorderingUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A local variable extracted from an expression, so that a generated check may test the variable instead of the original
 * expression. Unlike an arbitrary expression, a variable is evaluated exactly once and its value is tracked by the dataflow
 * analysis, so the generated check is taken into account at the places where the variable is used.
 *
 * @param variable  the declared variable
 * @param names     names to suggest for the variable; the first one is already used in the declaration
 * @param anchor    element the declaration was added before: either the {@link #statement()} itself, or an inspection
 *                  suppression comment that precedes it
 * @param statement statement that uses the variable
 */
record ExtractedVariable(@NotNull PsiLocalVariable variable,
                         @NotNull List<String> names,
                         @NotNull PsiElement anchor,
                         @NotNull PsiStatement statement) {

  /**
   * @return the statement that uses the variable, preceded by the inspection suppression comment that must stay attached
   * to it; the returned elements are adjacent siblings
   */
  PsiElement @NotNull [] statementWithSuppression() {
    return anchor == statement ? new PsiElement[]{statement} : new PsiElement[]{anchor, statement};
  }

  /**
   * @param expression expression to extract into a local variable
   * @return names to suggest for the new variable, the preferred one first;
   * an empty list if the expression cannot be extracted
   */
  static @NotNull List<String> suggestNames(@NotNull PsiExpression expression) {
    PsiExpression stripped = deparenthesize(expression);
    PsiType type = getVariableType(stripped);
    if (type == null) return List.of();
    if (PsiUtil.isAccessedForWriting(expression)) return List.of();
    if (!CodeBlockSurrounder.canSurround(expression)) return List.of();
    if (ReorderingUtils.canExtract(ExpressionUtils.getTopLevelExpression(expression), expression) == ThreeState.NO) return List.of();
    return suggestNames(stripped, type);
  }

  /**
   * Declares a new local variable initialized with the given expression before the enclosing statement and replaces
   * the expression with a reference to that variable.
   *
   * @param expression expression to extract; must belong to a writable file
   * @return the extracted variable, or null if the expression cannot be extracted
   */
  static @Nullable ExtractedVariable extract(@NotNull PsiExpression expression) {
    Project project = expression.getProject();
    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(expression);
    if (surrounder == null) return null;
    CodeBlockSurrounder.SurroundResult result = surrounder.surround();
    PsiExpression extracted = result.getExpression();
    PsiExpression initializer = deparenthesize(extracted);
    PsiType type = getVariableType(initializer);
    if (type == null) return null;
    List<String> names = suggestNames(initializer, type);
    String name = names.getFirst();

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiDeclarationStatement declaration = factory.createVariableDeclarationStatement(name, type, initializer, initializer);
    PsiElement anchor = result.getSuppressionAwareAnchor();
    PsiDeclarationStatement added = (PsiDeclarationStatement)anchor.getParent().addBefore(declaration, anchor);
    // a reference never needs the parentheses the original expression could have required
    PsiExpression toReplace = extracted;
    while (toReplace.getParent() instanceof PsiParenthesizedExpression parentheses) {
      toReplace = parentheses;
    }
    toReplace.replace(factory.createExpressionFromText(name, toReplace));

    PsiLocalVariable variable = ObjectUtils.tryCast(added.getDeclaredElements()[0], PsiLocalVariable.class);
    return variable == null ? null : new ExtractedVariable(variable, names, anchor, result.getAnchor());
  }

  private static @NotNull List<String> suggestNames(@NotNull PsiExpression expression, @NotNull PsiType type) {
    return new VariableNameGenerator(expression, VariableKind.LOCAL_VARIABLE).byExpression(expression).byType(type).generateAll(true);
  }

  private static @NotNull PsiExpression deparenthesize(@NotNull PsiExpression expression) {
    PsiExpression stripped = PsiUtil.skipParenthesizedExprDown(expression);
    return stripped == null ? expression : stripped;
  }

  private static @Nullable PsiType getVariableType(@NotNull PsiExpression expression) {
    PsiType type = GenericsUtil.getVariableTypeByExpressionType(expression.getType());
    if (type == null || PsiTypes.voidType().equals(type) || PsiTypes.nullType().equals(type)) return null;
    if (!PsiTypesUtil.isDenotableType(type, expression)) return null;
    // drop the type annotations: the variable is checked right after its declaration,
    // so a copied '@Nullable' would contradict the check
    if (!type.hasAnnotations()) return type;
    return JavaPsiFacade.getElementFactory(expression.getProject()).createTypeFromText(type.getCanonicalText(false), expression);
  }
}
