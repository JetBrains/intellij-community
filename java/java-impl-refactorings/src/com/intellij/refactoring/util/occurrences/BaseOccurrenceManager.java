// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.occurrences;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public abstract class BaseOccurrenceManager implements OccurrenceManager {
  private PsiExpression[] myOccurrences;
  private PsiElement myAnchorStatement;
  protected final OccurrenceFilter myFilter;

  public BaseOccurrenceManager(OccurrenceFilter filter) {
    myFilter = filter;
  }

  @Override
  public PsiExpression[] getOccurrences() {
    if(myOccurrences == null) {
      myOccurrences = findOccurrences();

      if(myFilter != null) {
        List<PsiExpression> result = new ArrayList<>();
        for (PsiExpression occurrence : myOccurrences) {
          if (myFilter.isOK(occurrence)) result.add(occurrence);
        }
        if (result.isEmpty()) {
          myOccurrences = defaultOccurrences();
        }
        else {
          myOccurrences = result.toArray(PsiExpression.EMPTY_ARRAY);
        }
      }

      if (getAnchorStatementForAll() == null) {
        myOccurrences = defaultOccurrences();
      }
    }
    return myOccurrences;
  }

  protected abstract PsiExpression @NotNull [] defaultOccurrences();

  protected abstract PsiExpression @NotNull [] findOccurrences();

  @Override
  public boolean isInFinalContext() {
    return needToDeclareFinal(myOccurrences);
  }

  @Override
  public PsiElement getAnchorStatementForAll() {
    if(myAnchorStatement == null) {
      myAnchorStatement = getAnchorStatementForAllInScope(null);
    }
    return myAnchorStatement;

  }
  @Override
  public PsiElement getAnchorStatementForAllInScope(PsiElement scope) {
    PsiElement anchor = CommonJavaRefactoringUtil.getAnchorElementForMultipleExpressions(myOccurrences, scope);
    return anchor instanceof PsiField && ((PsiField)anchor).hasInitializer() && !(anchor instanceof PsiEnumConstant) ? ((PsiField)anchor).getInitializer() : anchor;
  }

  private static boolean needToDeclareFinal(PsiExpression[] occurrences) {
    PsiElement scopeToDeclare = null;
    for (PsiExpression occurrence : occurrences) {
      final PsiElement data = occurrence.getUserData(ElementToWorkOn.PARENT);
      PsiElement element = data != null ? data : occurrence;
      if (scopeToDeclare == null) {
        scopeToDeclare = element;
      }
      else {
        scopeToDeclare = PsiTreeUtil.findCommonParent(scopeToDeclare, element);
      }
      if (PsiTreeUtil.getParentOfType(element, PsiSwitchLabelStatement.class, true, PsiStatement.class) != null) {
        return true;
      }
    }
    if(scopeToDeclare == null) {
      return false;
    }

    for (PsiExpression occurrence : occurrences) {
      PsiElement parent = occurrence.getUserData(ElementToWorkOn.PARENT);
      if (parent == null) parent = occurrence;
      while (!parent.equals(scopeToDeclare)) {
        parent = parent.getParent();
        if (parent instanceof PsiClass) {
          return !PsiUtil.isLanguageLevel8OrHigher(parent);
        }
      }
    }
    return false;
  }
}
