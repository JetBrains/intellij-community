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
package com.intellij.refactoring.util.occurrences;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.util.RefactoringUtil;
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
    PsiElement anchor = RefactoringUtil.getAnchorElementForMultipleExpressions(myOccurrences, scope);
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
          return true;
        }
      }
    }
    return false;
  }
}
