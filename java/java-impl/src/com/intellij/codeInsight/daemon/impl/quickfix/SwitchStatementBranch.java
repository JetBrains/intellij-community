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
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;

import java.util.*;

class SwitchStatementBranch {

  private final Set<PsiElement> myPendingDeclarations = new HashSet<>(5);
  private final List<String> myCaseValues = new ArrayList<>(2);
  private final List<PsiElement> myBodyElements = new ArrayList<>(5);
  private final List<PsiElement> myPendingWhiteSpace = new ArrayList<>(2);
  private boolean myDefault;
  private boolean myHasStatements;
  private boolean myAlwaysExecuted;

  public void addCaseValue(String labelString) {
    myCaseValues.add(labelString);
  }

  public void addStatement(PsiStatement statement) {
    myHasStatements = myHasStatements || !ControlFlowUtils.isEmpty(statement, false, true);
    addElement(statement);
  }

  public void addComment(PsiElement comment) {
    addElement(comment);
  }

  private void addElement(PsiElement element) {
    myBodyElements.addAll(myPendingWhiteSpace);
    myPendingWhiteSpace.clear();
    myBodyElements.add(element);
  }

  public void addWhiteSpace(PsiElement statement) {
    if (!myBodyElements.isEmpty()) {
      myPendingWhiteSpace.add(statement);
    }
  }

  public List<String> getCaseValues() {
    return Collections.unmodifiableList(myCaseValues);
  }

  public List<PsiElement> getBodyElements() {
    return Collections.unmodifiableList(myBodyElements);
  }

  public boolean isDefault() {
    return myDefault;
  }

  public void setDefault() {
    myDefault = true;
  }

  boolean isAlwaysExecuted() {
    return myAlwaysExecuted;
  }

  void setAlwaysExecuted(boolean alwaysExecuted) {
    myAlwaysExecuted = alwaysExecuted;
  }

  public boolean hasStatements() {
    return myHasStatements;
  }

  public void addPendingDeclarations(Set<? extends PsiElement> vars) {
    myPendingDeclarations.addAll(vars);
  }

  public Set<PsiElement> getPendingDeclarations() {
    return Collections.unmodifiableSet(myPendingDeclarations);
  }

  void addCaseValues(PsiSwitchLabelStatementBase label, boolean defaultAlwaysExecuted, CommentTracker commentTracker) {
    if (label.isDefaultCase()) {
      setDefault();
      setAlwaysExecuted(defaultAlwaysExecuted);
    }
    else {
      PsiExpressionList values = label.getCaseValues();
      if (values != null) {
        for (PsiExpression value : values.getExpressions()) {
          final String valueText = getCaseValueText(value, commentTracker);
          addCaseValue(valueText);
        }
      }
    }
  }

  private static String getCaseValueText(PsiExpression value, CommentTracker commentTracker) {
    value = PsiUtil.skipParenthesizedExprDown(value);
    if (value == null) {
      return "";
    }
    if (!(value instanceof PsiReferenceExpression)) {
      return commentTracker.text(value);
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)value;
    final PsiElement target = referenceExpression.resolve();

    if (!(target instanceof PsiEnumConstant)) {
      return commentTracker.text(value);
    }
    final PsiEnumConstant enumConstant = (PsiEnumConstant)target;
    final PsiClass aClass = enumConstant.getContainingClass();
    if (aClass == null) {
      return commentTracker.text(value);
    }
    return aClass.getQualifiedName() + '.' + commentTracker.text(referenceExpression);
  }
}