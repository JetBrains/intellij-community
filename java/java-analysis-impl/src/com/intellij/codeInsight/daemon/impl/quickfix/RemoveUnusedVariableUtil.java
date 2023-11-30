// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class RemoveUnusedVariableUtil {
  public enum RemoveMode {
    MAKE_STATEMENT,
    DELETE_ALL,
    CANCEL
  }

  @Contract("_, _, null -> false; null, _, _ -> false")
  public static boolean checkSideEffects(PsiExpression element,
                                         @Nullable PsiVariable variableToIgnore,
                                         List<? super PsiElement> sideEffects) {
    if (sideEffects == null || element == null) return false;
    List<PsiElement> writes = new ArrayList<>();
    Predicate<PsiElement> allowedSideEffect;
    if (variableToIgnore == null) {
      allowedSideEffect = e -> false;
    }
    else {
      allowedSideEffect = e -> e instanceof PsiAssignmentExpression &&
                               ExpressionUtils.isReferenceTo(((PsiAssignmentExpression)e).getLExpression(), variableToIgnore);
    }
    SideEffectChecker.checkSideEffects(element, writes, allowedSideEffect);
    sideEffects.addAll(writes);
    return !writes.isEmpty();
  }

  private static void replaceElementWithExpression(PsiExpression expression,
                                                   PsiElementFactory factory,
                                                   PsiElement element) throws IncorrectOperationException {
    PsiElement elementToReplace = element;
    PsiElement expressionToReplaceWith = expression;
    if (element.getParent() instanceof PsiExpressionStatement || element.getParent() instanceof PsiExpressionListStatement) {
      elementToReplace = element.getParent();
      expressionToReplaceWith = factory.createStatementFromText((expression == null ? "" : expression.getText()) + ";", null);
      if (isForLoopUpdate(elementToReplace)) {
        PsiElement lastChild = expressionToReplaceWith.getLastChild();
        if (PsiUtil.isJavaToken(lastChild, JavaTokenType.SEMICOLON)) {
          lastChild.delete();
        }
      }
    }
    else if (element.getParent() instanceof PsiDeclarationStatement) {
      expressionToReplaceWith = factory.createStatementFromText((expression == null ? "" : expression.getText()) + ";", null);
    }
    elementToReplace.replace(expressionToReplaceWith);
  }

  public static void deleteWholeStatement(@NotNull PsiElement element) {
    // just delete it altogether
    PsiElement parent = element.getParent();
    if (parent instanceof PsiExpressionStatement) {
      if (parent.getParent() instanceof PsiCodeBlock || isForLoopUpdate(parent)) {
        parent.delete();
      }
      else {
        // replace with empty statement (to handle with 'if (..) i=0;' )
        // if element used in expression, subexpression will do
        String replacement = parent.getParent() instanceof PsiSwitchLabeledRuleStatement ? "{}" : ";";
        PsiElement result = JavaPsiFacade.getElementFactory(parent.getProject()).createStatementFromText(replacement, null);
        parent.replace(result);
      }
    }
    else if (parent instanceof PsiExpressionList list && parent.getParent() instanceof PsiExpressionListStatement) {
      PsiExpression[] expressions = list.getExpressions();
      if (expressions.length == 2) {
        PsiExpression other = expressions[0] == element ? expressions[1] : expressions[0];
        replaceElementWithExpression(other, JavaPsiFacade.getElementFactory(parent.getProject()), parent);
      }
      else {
        element.delete();
      }
    }
    else if (element.getParent() instanceof PsiLambdaExpression) {
      element.replace(JavaPsiFacade.getElementFactory(parent.getProject()).createCodeBlock());
    }
    else {
      element.delete();
    }
  }

  public static boolean isForLoopUpdate(@Nullable PsiElement element) {
    if(element == null) return false;
    PsiElement parent = element.getParent();
    return parent instanceof PsiForStatement &&
           ((PsiForStatement)parent).getUpdate() == element;
  }
}
