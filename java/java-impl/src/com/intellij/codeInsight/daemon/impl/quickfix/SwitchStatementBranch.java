/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;

import java.util.*;

class SwitchStatementBranch {

  private final Set<PsiElement> myPendingDeclarations = new HashSet<>(5);
  private final List<PsiElement> myCaseElements = new ArrayList<>(2);
  private final List<PsiElement> myBodyElements = new ArrayList<>(5);
  private final List<PsiElement> myPendingWhiteSpace = new ArrayList<>(2);
  private boolean myDefault;
  private boolean myHasStatements;
  private boolean myAlwaysExecuted;

  void addStatement(PsiStatement statement) {
    myHasStatements = myHasStatements || !ControlFlowUtils.isEmpty(statement, false, true);
    addElement(statement);
  }

  void addComment(PsiElement comment) {
    addElement(comment);
  }

  private void addElement(PsiElement element) {
    myBodyElements.addAll(myPendingWhiteSpace);
    myPendingWhiteSpace.clear();
    myBodyElements.add(element);
  }

  void addWhiteSpace(PsiElement statement) {
    if (!myBodyElements.isEmpty()) {
      myPendingWhiteSpace.add(statement);
    }
  }

  List<PsiElement> getCaseElements() {
    return Collections.unmodifiableList(myCaseElements);
  }

  List<PsiElement> getBodyElements() {
    return Collections.unmodifiableList(myBodyElements);
  }

  boolean isDefault() {
    return myDefault;
  }

  boolean isAlwaysExecuted() {
    return myAlwaysExecuted;
  }

  boolean hasStatements() {
    return myHasStatements;
  }

  void addPendingDeclarations(Set<? extends PsiElement> vars) {
    myPendingDeclarations.addAll(vars);
  }

  public Set<PsiElement> getPendingDeclarations() {
    return Collections.unmodifiableSet(myPendingDeclarations);
  }

  void addCaseValues(PsiSwitchLabelStatementBase label, boolean defaultAlwaysExecuted) {
    if (label.isDefaultCase()) {
      myDefault = true;
      myAlwaysExecuted = defaultAlwaysExecuted;
    } else {
      PsiExpression nullCase = null;
      PsiCaseLabelElementList labelElementList = label.getCaseLabelElementList();
      if (labelElementList != null) {
        for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
          if (labelElement instanceof PsiDefaultCaseLabelElement) {
            myDefault = true;
            myAlwaysExecuted = defaultAlwaysExecuted;
            break;
          }
          else if (labelElement instanceof PsiExpression expression && ExpressionUtils.isNullLiteral(expression)) {
            nullCase = expression;
          }
        }
        if (!myDefault) {
          Collections.addAll(myCaseElements, labelElementList.getElements());
        } else if (nullCase != null) {
          myCaseElements.add(nullCase);
        }
      }
    }
  }
}