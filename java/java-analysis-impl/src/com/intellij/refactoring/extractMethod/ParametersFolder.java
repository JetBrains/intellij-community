/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.refactoring.util.VariableData;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ParametersFolder {
  private final Map<PsiVariable, PsiExpression> myExpressions = new HashMap<>();
  private final Map<PsiVariable, String> myArgs = new HashMap<>();
  private final Map<PsiVariable, List<PsiExpression>> myMentionedInExpressions = new HashMap<>();

  private final Set<PsiVariable> myDeleted = new HashSet<>();
  private boolean myFoldingSelectedByDefault;


  public void clear() {
    myExpressions.clear();
    myMentionedInExpressions.clear();
    myDeleted.clear();
  }

  public boolean isParameterSafeToDelete(@NotNull VariableData data, @NotNull LocalSearchScope scope) {
    Next:
    for (PsiReference reference : ReferencesSearch.search(data.variable, scope)) {
      PsiElement expression = reference.getElement();
      while (expression != null) {
        for (PsiExpression psiExpression : myExpressions.values()) {
          if (PsiEquivalenceUtil.areElementsEquivalent(expression, psiExpression)) {
            continue Next;
          }
        }
        expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
      }
      return false;
    }
    final PsiExpression psiExpression = myExpressions.get(data.variable);
    if (psiExpression == null) return true;
    for (PsiVariable variable : myExpressions.keySet()) {
      if (variable != data.variable && !myDeleted.contains(variable)) {
        final PsiExpression expr = myExpressions.get(variable);
        if (expr != null && PsiEquivalenceUtil.areElementsEquivalent(expr, psiExpression)) {
          myDeleted.add(data.variable);
          return true;
        }
      }
    }
    return false;
  }

  public void foldParameterUsagesInBody(@NotNull List<VariableData> datum, PsiElement[] elements, SearchScope scope) {
    Map<VariableData, Set<PsiExpression>> equivalentExpressions = new LinkedHashMap<>();
    for (VariableData data : datum) {
      if (myDeleted.contains(data.variable)) continue;
      final PsiExpression psiExpression = myExpressions.get(data.variable);
      if (psiExpression == null) continue;

      final Set<PsiExpression> eqExpressions = new HashSet<>();
      for (PsiReference reference : ReferencesSearch.search(data.variable, scope)) {
        final PsiExpression expression = findEquivalent(psiExpression, reference.getElement());
        if (expression != null && expression.isValid()) {
          eqExpressions.add(expression);
        }
      }

      equivalentExpressions.put(data, eqExpressions);
    }

    for (VariableData data : equivalentExpressions.keySet()) {
      final Set<PsiExpression> eqExpressions = equivalentExpressions.get(data);
      for (PsiExpression expression : eqExpressions) {
        if (!expression.isValid()) continue; //was replaced on previous step
        final PsiExpression refExpression =
          JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(data.name, expression);
        final PsiElement replaced = expression.replace(refExpression);
        for (int i = 0, psiElementsLength = elements.length; i < psiElementsLength; i++) {
          PsiElement psiElement = elements[i];
          if (expression == psiElement) {
            elements[i] = replaced;
            break;
          }
        }
      }
    }
  }

  boolean isParameterFoldable(@NotNull VariableData data,
                              @NotNull LocalSearchScope scope,
                              @NotNull final List<? extends PsiVariable> inputVariables,
                              UniqueNameGenerator nameGenerator, String defaultName) {
    final List<PsiExpression> mentionedInExpressions = getMentionedExpressions(data.variable, scope, inputVariables);
    if (mentionedInExpressions == null) return false;

    int currentRank = 0;
    PsiExpression mostRanked = null;
    for (int i = mentionedInExpressions.size() - 1; i >= 0; i--) {
      PsiExpression expression = mentionedInExpressions.get(i);
      boolean arrayAccess = expression instanceof PsiArrayAccessExpression && !isConditional(expression, scope);
      if (arrayAccess) {
        myFoldingSelectedByDefault = true;
      }
      final int r = findUsedVariables(data, inputVariables, expression).size();
      if (currentRank < r || arrayAccess && currentRank == r) {
        currentRank = r;
        mostRanked = expression;
      }
    }

    if (mostRanked != null) {
      myExpressions.put(data.variable, mostRanked);
      myArgs.put(data.variable, mostRanked.getText());
      data.type = RefactoringChangeUtil.getTypeByExpression(mostRanked);
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(mostRanked.getProject());
      final SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, mostRanked, data.type);
      if (nameInfo.names.length > 0 &&
          !Comparing.equal(nameInfo.names[0], data.name) &&
          !Comparing.equal(nameInfo.names[0], defaultName)) {
        data.name = nameInfo.names[0];
        setUniqueName(data, nameGenerator, scope, mostRanked);
      }
    }

    return mostRanked != null;
  }

  private static boolean isConditional(PsiElement expr, LocalSearchScope scope) {
    while (expr != null) {
      final PsiElement parent = expr.getParent();
      if (parent != null && scope.containsRange(parent.getContainingFile(), parent.getTextRange())) {
        if (parent instanceof PsiIfStatement) {
          if (((PsiIfStatement)parent).getCondition() != expr) return true;
        } else if (parent instanceof PsiConditionalExpression) {
          if (((PsiConditionalExpression)parent).getCondition() != expr) return true;
        } else if (parent instanceof PsiSwitchStatement) {
          if (((PsiSwitchStatement)parent).getExpression() != expr) return true;
        }
      } else {
        return false;
      }
      expr = parent;
    }
    return false;
  }

  private static void setUniqueName(VariableData data, UniqueNameGenerator nameGenerator,
                                    LocalSearchScope scope, PsiExpression expr) {
    String name = data.name;
    int idx = 1;
    while (true) {
      if (nameGenerator.isUnique(name, "", "")) {
        final PsiVariable definedVariable = PsiResolveHelper.SERVICE.getInstance(expr.getProject()).resolveReferencedVariable(name, expr);
        if (definedVariable == null || !scope.containsRange(expr.getContainingFile(), definedVariable.getTextRange())) {
          data.name = name;
          nameGenerator.addExistingName(name);
          break;
        }
      }
      name = data.name + idx++;
    }
  }

  private static Set<PsiVariable> findUsedVariables(VariableData data, final List<? extends PsiVariable> inputVariables,
                                             PsiExpression expression) {
    final Set<PsiVariable> found = new HashSet<>();
    expression.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);
        PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof PsiVariable && inputVariables.contains(resolved)) {
          found.add((PsiVariable)resolved);
        }
      }
    });
    found.remove(data.variable);
    return found;
  }

  public boolean isFoldable() {
    return !myExpressions.isEmpty();
  }

  @Nullable
  private List<PsiExpression> getMentionedExpressions(PsiVariable var, LocalSearchScope scope, final List<? extends PsiVariable> inputVariables) {
    if (myMentionedInExpressions.containsKey(var)) return myMentionedInExpressions.get(var);
    final PsiElement[] scopeElements = scope.getScope();
    List<PsiExpression> expressions = null;
    for (PsiReference reference : ReferencesSearch.search(var, scope)) {
      PsiElement expression = reference.getElement();
      if (expressions == null) {
        expressions = new ArrayList<>();
        while (expression != null) {
          if (isAccessedForWriting((PsiExpression)expression)) {
            return null;
          }
          if (isAncestor(expression, scopeElements)) {
            break;
          }
          if (dependsOnLocals(expression, inputVariables)) {
            break;
          }
          final PsiElement parent = expression.getParent();
          if (parent instanceof PsiExpressionStatement) {
            break;
          }
          final PsiType expressionType = ((PsiExpression)expression).getType();
          if (expressionType == null || PsiType.VOID.equals(expressionType)) {
            break;
          }
          if (isTooLongExpressionChain(expression)) {
            break;
          }
          if (!isMethodNameExpression(expression)) {
            expressions.add((PsiExpression)expression);
          }
          expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
        }
      }
      else {
        for (Iterator<PsiExpression> iterator = expressions.iterator(); iterator.hasNext();) {
          if (findEquivalent(iterator.next(), expression) == null) {
            iterator.remove();
          }
        }
      }
    }
    myMentionedInExpressions.put(var, expressions);
    return expressions;
  }

  private static boolean isAccessedForWriting(PsiExpression expression) {
    final PsiExpression[] exprWithWriteAccessInside = new PsiExpression[1];
    expression.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (exprWithWriteAccessInside[0] != null) return;
        super.visitElement(element);
      }

      @Override
      public void visitExpression(PsiExpression expression) {
        if (PsiUtil.isAccessedForWriting(expression)) {
          exprWithWriteAccessInside[0] = expression;
        }
        super.visitExpression(expression);
      }
    });
    return exprWithWriteAccessInside[0] != null;
  }

  private static boolean isAncestor(PsiElement expression, PsiElement[] scopeElements) {
    for (PsiElement scopeElement : scopeElements) {
      if (PsiTreeUtil.isAncestor(expression, scopeElement, true)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isTooLongExpressionChain(PsiElement expression) {
    int count = 0;
    for (PsiElement element = getInnerExpression(expression); element != null; element = getInnerExpression(element)) {
      count++;
      if (count > 1) { // expression chains like 'var.foo().bar()' and 'var.foo[i].bar()' are too long
        return true;
      }
    }
    return false;
  }

  private static PsiElement getInnerExpression(PsiElement expression) {
    if (expression instanceof PsiMethodCallExpression) {
      return ((PsiMethodCallExpression)expression).getMethodExpression().getQualifierExpression();
    }
    if (expression instanceof PsiArrayAccessExpression) {
      while (expression instanceof PsiArrayAccessExpression) {
        expression = ((PsiArrayAccessExpression)expression).getArrayExpression();
      }
      return expression;
    }
    return null;
  }

  private static boolean isMethodNameExpression(@NotNull PsiElement expression) {
    final PsiElement parent = expression.getParent();
    return expression instanceof PsiReferenceExpression &&
           parent instanceof PsiMethodCallExpression &&
           ((PsiReferenceExpression)expression).getReferenceNameElement() ==
           ((PsiMethodCallExpression)parent).getMethodExpression().getReferenceNameElement();
  }

  private static boolean dependsOnLocals(final PsiElement expression, final List<? extends PsiVariable> inputVariables) {
    final boolean[] localVarsUsed = new boolean[]{false};
    expression.accept(new JavaRecursiveElementWalkingVisitor(){
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiVariable) {
          final PsiVariable variable = (PsiVariable)resolved;
          if (!(variable instanceof PsiField) && !inputVariables.contains(variable)) {
            localVarsUsed[0] = true;
            return;
          }
        }
        super.visitReferenceExpression(expression);
      }
    });
    return localVarsUsed[0];
  }

  @NotNull
  public String getGeneratedCallArgument(@NotNull VariableData data) {
    return myArgs.containsKey(data.variable) ? myArgs.get(data.variable) : data.variable.getName();
  }

  public boolean annotateWithParameter(@NotNull VariableData data, @NotNull PsiElement element) {
    final PsiExpression psiExpression = myExpressions.get(data.variable);
    if (psiExpression != null) {
      final PsiExpression expression = findEquivalent(psiExpression, element);
      if (expression != null) {
        expression.putUserData(DuplicatesFinder.PARAMETER, Pair.create(data.variable, expression.getType()));
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static PsiExpression findEquivalent(PsiExpression expr, PsiElement element) {
    PsiElement expression = element;
    while (expression  != null) {
      if (PsiEquivalenceUtil.areElementsEquivalent(expression, expr)) {
        PsiExpression psiExpression = (PsiExpression)expression;
        return PsiUtil.isAccessedForWriting(psiExpression) ? null : psiExpression;
      }
      expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
    }
    return null;
  }

  public boolean wasExcluded(PsiVariable variable) {
    return myDeleted.contains(variable) || (myMentionedInExpressions.containsKey(variable) && myExpressions.get(variable) == null);
  }

  public boolean isFoldingSelectedByDefault() {
    return myFoldingSelectedByDefault;
  }
}