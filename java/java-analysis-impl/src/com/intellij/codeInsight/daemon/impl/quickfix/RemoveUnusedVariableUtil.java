// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
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

  public static RemoveMode getModeForPreview(PsiExpression element, @Nullable PsiVariable variableToIgnore) {
    List<PsiElement> sideEffects = new ArrayList<>();
    boolean hasSideEffects = checkSideEffects(element, variableToIgnore, sideEffects);
    if (!hasSideEffects || sideEffects.isEmpty()) return RemoveMode.DELETE_ALL;
    return RemoveMode.MAKE_STATEMENT;
  }

  private static PsiElement replaceElementWithExpression(PsiExpression expression,
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
    return elementToReplace.replace(expressionToReplaceWith);
  }

  private static PsiElement createStatementIfNeeded(PsiExpression expression,
                                                    PsiElementFactory factory,
                                                    PsiElement element) throws IncorrectOperationException {
    // if element used in expression, subexpression will do
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiExpressionStatement) && !(parent instanceof PsiDeclarationStatement)) {
      return expression;
    }
    String replacement;
    if (expression == null) {
      boolean needBlock = parent instanceof PsiExpressionStatement && parent.getParent() instanceof PsiSwitchLabeledRuleStatement;
      replacement = needBlock ? "{}" : ";";
    }
    else {
      replacement = expression.getText() + ";";
    }
    return factory.createStatementFromText(replacement, null);
  }

  private static void deleteWholeStatement(PsiElement element, PsiElementFactory factory)
    throws IncorrectOperationException {
    // just delete it altogether
    PsiElement parent = element.getParent();
    if (parent instanceof PsiExpressionStatement) {
      if (parent.getParent() instanceof PsiCodeBlock || isForLoopUpdate(parent)) {
        parent.delete();
      }
      else {
        // replace with empty statement (to handle with 'if (..) i=0;' )
        parent.replace(createStatementIfNeeded(null, factory, element));
      }
    }
    else if (parent instanceof PsiExpressionList list && parent.getParent() instanceof PsiExpressionListStatement) {
      PsiExpression[] expressions = list.getExpressions();
      if (expressions.length == 2) {
        PsiExpression other = expressions[0] == element ? expressions[1] : expressions[0];
        replaceElementWithExpression(other, factory, parent);
      }
      else {
        element.delete();
      }
    }
    else if (element.getParent() instanceof PsiLambdaExpression) {
      element.replace(factory.createCodeBlock());
    }
    else {
      element.delete();
    }
  }

  static void deleteReferences(PsiVariable variable, List<? extends PsiElement> references, @NotNull RemoveMode mode) throws IncorrectOperationException {
    for (PsiElement expression : references) {
      processUsage(expression, variable, null, mode);
    }
  }

  static void collectReferences(@NotNull PsiElement context, final PsiVariable variable, final List<? super PsiElement> references) {
    context.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        if (expression.resolve() == variable) references.add(expression);
        super.visitReferenceExpression(expression);
      }
    });
  }

  /**
   * @param sideEffects if null, delete usages, otherwise collect side effects
   * @return true if there are at least one unrecoverable side effect found, false if no side effects,
   *         null if read usage found (may happen if interval between fix creation in invoke() call was long enough)
   */
  static Boolean processUsage(PsiElement element, PsiVariable variable, List<? super PsiElement> sideEffects, @NotNull RemoveMode deleteMode)
    throws IncorrectOperationException {
    if (!element.isValid()) return null;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(variable.getProject());
    while (element != null) {
      if (element instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression expression = (PsiAssignmentExpression)element;
        PsiExpression lExpression = expression.getLExpression();
        // there should not be read access to the variable, otherwise it is not unused
        if (!(lExpression instanceof PsiReferenceExpression) || variable != ((PsiReferenceExpression)lExpression).resolve()) {
          return null;
        }
        PsiExpression rExpression = expression.getRExpression();
        rExpression = PsiUtil.skipParenthesizedExprDown(rExpression);
        if (rExpression == null) return true;
        // replace assignment with expression and re-simplify
        boolean sideEffectFound = checkSideEffects(rExpression, variable, sideEffects);
        if (!ExpressionUtils.isVoidContext(expression) || PsiUtil.isStatement(rExpression)) {
          if (deleteMode == RemoveMode.MAKE_STATEMENT ||
              deleteMode == RemoveMode.DELETE_ALL && !ExpressionUtils.isVoidContext(expression)) {
            element = replaceElementWithExpression(rExpression, factory, element);
            element = eraseUnnecessaryOuterParentheses(element);
            List<PsiElement> references = new ArrayList<>();
            collectReferences(element, variable, references);
            deleteReferences(variable, references, deleteMode);
          }
          else if (deleteMode == RemoveMode.DELETE_ALL) {
            deleteWholeStatement(element, factory);
          }
          return true;
        }
        else {
          if (deleteMode != RemoveMode.CANCEL) {
            deleteWholeStatement(element, factory);
          }
          return !sideEffectFound;
        }
      }
      else if (element instanceof PsiExpressionStatement && deleteMode != RemoveMode.CANCEL) {
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiIfStatement || parent instanceof PsiLoopStatement && ((PsiLoopStatement)parent).getBody() == element) {
          element.replace(JavaPsiFacade.getElementFactory(element.getProject()).createStatementFromText(";", element));
        } else {
          element.delete();
        }
        break;
      }
      else if (element instanceof PsiVariable && element == variable) {
        PsiExpression expression = variable.getInitializer();
        if (expression != null) {
          expression = PsiUtil.deparenthesizeExpression(expression);
        }
        boolean sideEffectsFound = checkSideEffects(expression, variable, sideEffects);
        if (expression != null && PsiUtil.isStatement(expression) && variable instanceof PsiLocalVariable
            &&
            !(variable.getParent() instanceof PsiDeclarationStatement &&
              ((PsiDeclarationStatement)variable.getParent()).getDeclaredElements().length > 1)) {
          if (deleteMode == RemoveMode.MAKE_STATEMENT) {
            element = element.getParent().replace(createStatementIfNeeded(expression, factory, element));
            List<PsiElement> references = new ArrayList<>();
            collectReferences(element, variable, references);
            deleteReferences(variable, references, deleteMode);
          }
          else if (deleteMode == RemoveMode.DELETE_ALL) {
            deleteVariable(variable);
          }
          return true;
        }
        else {
          if (deleteMode != RemoveMode.CANCEL) {
            if (element instanceof PsiField) {
              ((PsiField)element).normalizeDeclaration();
            }
            deleteVariable(variable);
          }
          return !sideEffectsFound;
        }
      }
      element = element.getParent();
    }
    return true;
  }

  private static void deleteVariable(PsiVariable variable) {
    CommentTracker tracker = new CommentTracker();
    tracker.markUnchanged(variable.getInitializer()); // assume that initializer is used (e.g. inlined)
    if (variable instanceof PsiJavaDocumentedElement) {
      tracker.markUnchanged(((PsiJavaDocumentedElement)variable).getDocComment());
    }
    tracker.deleteAndRestoreComments(variable);
  }

  @NotNull
  private static PsiElement eraseUnnecessaryOuterParentheses(@NotNull PsiElement element) {
    PsiElement parenthesizedParent = element;
    while (parenthesizedParent.getParent() instanceof PsiParenthesizedExpression) {
      parenthesizedParent = parenthesizedParent.getParent();
    }
    if (parenthesizedParent != element) {
      // replace() will preserve the parentheses if they're mandatory due to operator precedence
      return parenthesizedParent.replace(element);
    }
    return element;
  }

  public static boolean isForLoopUpdate(@Nullable PsiElement element) {
    if(element == null) return false;
    PsiElement parent = element.getParent();
    return parent instanceof PsiForStatement &&
           ((PsiForStatement)parent).getUpdate() == element;
  }
}
