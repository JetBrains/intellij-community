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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;

import java.util.*;

class SwitchStatementBranch {

  private final Set<PsiLocalVariable> m_pendingVariableDeclarations =
    new HashSet<>(5);
  private final List<String> m_caseValues =
    new ArrayList<>(2);
  private final List<PsiElement> m_bodyElements =
    new ArrayList<>(5);
  private final List<PsiElement> m_pendingWhiteSpace =
    new ArrayList<>(2);
  private boolean m_default;
  private boolean m_hasStatements;

  public void addCaseValue(String labelString) {
    m_caseValues.add(labelString);
  }

  public void addStatement(PsiElement statement) {
    m_hasStatements = true;
    addElement(statement);
  }

  public void addComment(PsiElement comment) {
    addElement(comment);
  }

  private void addElement(PsiElement element) {
    m_bodyElements.addAll(m_pendingWhiteSpace);
    m_pendingWhiteSpace.clear();
    m_bodyElements.add(element);
  }

  public void addWhiteSpace(PsiElement statement) {
    if (!m_bodyElements.isEmpty()) {
      m_pendingWhiteSpace.add(statement);
    }
  }

  public List<String> getCaseValues() {
    return Collections.unmodifiableList(m_caseValues);
  }

  public List<PsiElement> getBodyElements() {
    return Collections.unmodifiableList(m_bodyElements);
  }

  public boolean isDefault() {
    return m_default;
  }

  public void setDefault() {
    m_default = true;
  }

  public boolean hasStatements() {
    return m_hasStatements;
  }

  public void addPendingVariableDeclarations(Set<PsiLocalVariable> vars) {
    m_pendingVariableDeclarations.addAll(vars);
  }

  public Set<PsiLocalVariable> getPendingVariableDeclarations() {
    return Collections.unmodifiableSet(m_pendingVariableDeclarations);
  }
}