/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RemoveUnusedVariableUtil {
  public enum RemoveMode {
    MAKE_STATEMENT,
    DELETE_ALL,
    CANCEL
  }

  public static boolean checkSideEffects(PsiExpression element, @Nullable PsiVariable variableToIgnore, List<PsiElement> sideEffects) {
    if (sideEffects == null || element == null) return false;
    List<PsiElement> writes = new ArrayList<>();
    SideEffectChecker.checkSideEffects(element, writes);
    if (variableToIgnore != null) {
      for (int i = writes.size() - 1; i >= 0; i--) {
        PsiElement write = writes.get(i);
        if (!(write instanceof PsiAssignmentExpression)) continue;
        PsiExpression lExpression = ((PsiAssignmentExpression)write).getLExpression();
        if (lExpression instanceof PsiReference && ((PsiReference)lExpression).resolve() == variableToIgnore) {
          writes.remove(i);
        }
      }
    }
    sideEffects.addAll(writes);
    return !writes.isEmpty();
  }

  static PsiElement replaceElementWithExpression(PsiExpression expression,
                                                 PsiElementFactory factory,
                                                 PsiElement element) throws IncorrectOperationException {
    PsiElement elementToReplace = element;
    PsiElement expressionToReplaceWith = expression;
    if (element.getParent() instanceof PsiExpressionStatement) {
      elementToReplace = element.getParent();
      expressionToReplaceWith =
      factory.createStatementFromText((expression == null ? "" : expression.getText()) + ";", null);
    }
    else if (element.getParent() instanceof PsiDeclarationStatement) {
      expressionToReplaceWith =
      factory.createStatementFromText((expression == null ? "" : expression.getText()) + ";", null);
    }
    return elementToReplace.replace(expressionToReplaceWith);
  }

  static PsiElement createStatementIfNeeded(PsiExpression expression,
                                            PsiElementFactory factory,
                                            PsiElement element) throws IncorrectOperationException {
    // if element used in expression, subexpression will do
    if (!(element.getParent() instanceof PsiExpressionStatement) &&
        !(element.getParent() instanceof PsiDeclarationStatement)) {
      return expression;
    }
    return factory.createStatementFromText((expression == null ? "" : expression.getText()) + ";", null);
  }

  static void deleteWholeStatement(PsiElement element, PsiElementFactory factory)
    throws IncorrectOperationException {
    // just delete it altogether
    if (element.getParent() instanceof PsiExpressionStatement) {
      PsiExpressionStatement parent = (PsiExpressionStatement)element.getParent();
      if (parent.getParent() instanceof PsiCodeBlock) {
        parent.delete();
      }
      else {
        // replace with empty statement (to handle with 'if (..) i=0;' )
        parent.replace(createStatementIfNeeded(null, factory, element));
      }
    }
    else {
      element.delete();
    }
  }

  static void deleteReferences(PsiVariable variable, List<PsiElement> references, @NotNull RemoveMode mode) throws IncorrectOperationException {
    for (PsiElement expression : references) {
      processUsage(expression, variable, null, mode);
    }
  }

  static void collectReferences(@NotNull PsiElement context, final PsiVariable variable, final List<PsiElement> references) {
    context.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.resolve() == variable) references.add(expression);
        super.visitReferenceExpression(expression);
      }
    });
  }

  /**
   * @param sideEffects if null, delete usages, otherwise collect side effects
   * @return true if there are at least one unrecoverable side effect found, false if no side effects,
   *         null if read usage found (may happen if interval between fix creation in invoke() call was long enough)
   * @throws com.intellij.util.IncorrectOperationException
   */
  static Boolean processUsage(PsiElement element, PsiVariable variable, List<PsiElement> sideEffects, @NotNull RemoveMode deleteMode)
    throws IncorrectOperationException {
    if (!element.isValid()) return null;
    PsiElementFactory factory = JavaPsiFacade.getInstance(variable.getProject()).getElementFactory();
    while (element != null) {
      if (element instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression expression = (PsiAssignmentExpression)element;
        PsiExpression lExpression = expression.getLExpression();
        // there should not be read access to the variable, otherwise it is not unused
        if (!(lExpression instanceof PsiReferenceExpression) || variable != ((PsiReferenceExpression)lExpression).resolve()) {
          return null;
        }
        PsiExpression rExpression = expression.getRExpression();
        rExpression = PsiUtil.deparenthesizeExpression(rExpression);
        if (rExpression == null) return true;
        // replace assignment with expression and resimplify
        boolean sideEffectFound = checkSideEffects(rExpression, variable, sideEffects);
        if (!(element.getParent() instanceof PsiExpressionStatement) || PsiUtil.isStatement(rExpression)) {
          if (deleteMode == RemoveMode.MAKE_STATEMENT ||
              deleteMode == RemoveMode.DELETE_ALL && !(element.getParent() instanceof PsiExpressionStatement)) {
            element = replaceElementWithExpression(rExpression, factory, element);
            while (element.getParent() instanceof PsiParenthesizedExpression) {
              element = element.getParent().replace(element);
            }
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
            element.delete();
          }
          return true;
        }
        else {
          if (deleteMode != RemoveMode.CANCEL) {
            if (element instanceof PsiField) {
              ((PsiField)element).normalizeDeclaration();
            }
            element.delete();
          }
          return !sideEffectsFound;
        }
      }
      element = element.getParent();
    }
    return true;
  }
}
