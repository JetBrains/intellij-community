// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.PsiEquivalenceUtil;
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

  void clear() {
    myExpressions.clear();
    myMentionedInExpressions.clear();
    myDeleted.clear();
  }

  boolean isParameterSafeToDelete(@NotNull VariableData data, @NotNull LocalSearchScope scope) {
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

  void foldParameterUsagesInBody(@NotNull List<? extends VariableData> datum, PsiElement @NotNull [] elements, @NotNull SearchScope scope) {
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
                              @NotNull List<? extends PsiVariable> inputVariables,
                              @NotNull UniqueNameGenerator nameGenerator,
                              @NotNull String defaultName) {
    final List<PsiExpression> mentionedInExpressions = getMentionedExpressions(data.variable, scope, inputVariables);
    if (mentionedInExpressions == null) return false;

    int currentRank = 0;
    PsiExpression mostRanked = null;
    for (int i = mentionedInExpressions.size() - 1; i >= 0; i--) {
      PsiExpression expression = mentionedInExpressions.get(i);
      final int r = findUsedVariables(data, inputVariables, expression).size();
      if (currentRank < r || expression instanceof PsiArrayAccessExpression && myFoldingSelectedByDefault && currentRank == r) {
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
          !Objects.equals(nameInfo.names[0], data.name) &&
          !Objects.equals(nameInfo.names[0], defaultName)) {
        data.name = nameInfo.names[0];
        setUniqueName(data, nameGenerator, mostRanked, codeStyleManager);
      }
    }

    return mostRanked != null;
  }

  private static void setUniqueName(@NotNull VariableData data, @NotNull UniqueNameGenerator nameGenerator,
                                    @NotNull PsiExpression expr, @NotNull JavaCodeStyleManager codeStyleManager) {
    String name = data.name;
    int idx = 1;
    while (true) {
      if (nameGenerator.isUnique(name, "", "") &&
          name.equals(codeStyleManager.suggestUniqueVariableName(name, expr, true))) {
        data.name = name;
        nameGenerator.addExistingName(name);
        break;
      }
      name = data.name + idx++;
    }
  }

  private static @NotNull Set<PsiVariable> findUsedVariables(@NotNull VariableData data, @NotNull List<? extends PsiVariable> inputVariables,
                                                             @NotNull PsiExpression expression) {
    final Set<PsiVariable> found = new HashSet<>();
    expression.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression referenceExpression) {
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

  boolean isFoldable() {
    return !myExpressions.isEmpty();
  }

  private @Nullable List<PsiExpression> getMentionedExpressions(@NotNull PsiVariable var, @NotNull LocalSearchScope scope, @NotNull List<? extends PsiVariable> inputVariables) {
    if (myMentionedInExpressions.containsKey(var)) return myMentionedInExpressions.get(var);
    final PsiElement[] scopeElements = scope.getScope();

    List<PsiExpression> expressions = null;
    Boolean arrayAccess = null;
    List<PsiElement> refExpressions = ReferencesSearch.search(var, scope).findAll().stream()
      .map(ref -> ref.getElement())
      .sorted(Comparator.comparingInt(element -> element.getTextRange().getStartOffset()))
      .toList();
    for (PsiElement expression : refExpressions) {
      if (expressions == null) {
        expressions = new ArrayList<>();
        while (expression instanceof PsiExpression) {
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
          if (expressionType == null || PsiTypes.voidType().equals(expressionType)) {
            break;
          }
          if (isTooLongExpressionChain(expression)) {
            break;
          }
          if (!isMethodNameExpression(expression)) {
            expressions.add((PsiExpression)expression);
          }
          if (expression instanceof PsiArrayAccessExpression && (arrayAccess == null || arrayAccess)) {
            arrayAccess = isSafeToFoldArrayAccess(scope, expression);
          }
          expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
        }
      }
      else {
        for (Iterator<PsiExpression> iterator = expressions.iterator(); iterator.hasNext();) {
          PsiExpression equivalent = findEquivalent(iterator.next(), expression);
          if (equivalent == null) {
            iterator.remove();
          }
          else if (equivalent instanceof PsiArrayAccessExpression && (arrayAccess == null || arrayAccess)) {
            arrayAccess = isSafeToFoldArrayAccess(scope, equivalent);
          }
        }
      }
    }
    if (arrayAccess != null && arrayAccess) {
      myFoldingSelectedByDefault = true;
    }
    myMentionedInExpressions.put(var, expressions);
    return expressions;
  }

  public static boolean isSafeToFoldArrayAccess(@NotNull LocalSearchScope scope,
                                                 PsiElement expression) {
    while (true) {
      final PsiElement parent = expression.getParent();
      if (parent != null && scope.containsRange(parent.getContainingFile(), parent.getTextRange())) {
        if (parent instanceof PsiIfStatement ||
            parent instanceof PsiConditionalExpression ||
            parent instanceof PsiSwitchStatement) {
          return false;
        }
      }
      else {
        return true;
      }
      expression = parent;
    }
  }

  private static boolean isAccessedForWriting(@NotNull PsiExpression expression) {
    final PsiExpression[] exprWithWriteAccessInside = new PsiExpression[1];
    expression.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (exprWithWriteAccessInside[0] != null) return;
        super.visitElement(element);
      }

      @Override
      public void visitExpression(@NotNull PsiExpression expression) {
        if (PsiUtil.isAccessedForWriting(expression)) {
          exprWithWriteAccessInside[0] = expression;
        }
        super.visitExpression(expression);
      }
    });
    return exprWithWriteAccessInside[0] != null;
  }

  private static boolean isAncestor(@NotNull PsiElement expression, PsiElement @NotNull [] scopeElements) {
    for (PsiElement scopeElement : scopeElements) {
      if (PsiTreeUtil.isAncestor(expression, scopeElement, false)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isTooLongExpressionChain(@NotNull PsiElement expression) {
    int count = 0;
    for (PsiElement element = getInnerExpression(expression); element != null; element = getInnerExpression(element)) {
      count++;
      if (count > 1) { // expression chains like 'var.foo().bar()' and 'var.foo[i].bar()' are too long
        return true;
      }
    }
    return false;
  }

  private static PsiElement getInnerExpression(@NotNull PsiElement expression) {
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

  private static boolean dependsOnLocals(@NotNull PsiElement expression, @NotNull List<? extends PsiVariable> inputVariables) {
    final boolean[] localVarsUsed = {false};
    expression.accept(new JavaRecursiveElementWalkingVisitor(){
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        final PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiVariable variable) {
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
  String getGeneratedCallArgument(@NotNull VariableData data) {
    return myArgs.containsKey(data.variable) ? myArgs.get(data.variable) : data.variable.getName();
  }

  void putCallArgument(@NotNull PsiVariable argument, @NotNull PsiExpression value) {
    myArgs.put(argument, value.getText());
  }

  boolean annotateWithParameter(@NotNull VariableData data, @NotNull PsiElement element) {
    final PsiExpression psiExpression = myExpressions.get(data.variable);
    if (psiExpression != null) {
      final PsiExpression expression = findEquivalent(psiExpression, element);
      if (expression != null) {
        expression.putUserData(DuplicatesFinder.PARAMETER, new DuplicatesFinder.Parameter(data.variable, expression.getType(), true));
        return true;
      }
    }
    return false;
  }

  private static @Nullable PsiExpression findEquivalent(PsiExpression expr, @NotNull PsiElement element) {
    PsiElement expression = element;
    while (expression != null) {
      if (PsiEquivalenceUtil.areElementsEquivalent(expression, expr)) {
        PsiExpression psiExpression = (PsiExpression)expression;
        return PsiUtil.isAccessedForWriting(psiExpression) ? null : psiExpression;
      }
      expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
    }
    return null;
  }

  boolean wasExcluded(@NotNull PsiVariable variable) {
    return myDeleted.contains(variable) || myMentionedInExpressions.containsKey(variable) && myExpressions.get(variable) == null;
  }

  boolean isFoldingSelectedByDefault() {
    return myFoldingSelectedByDefault;
  }
}