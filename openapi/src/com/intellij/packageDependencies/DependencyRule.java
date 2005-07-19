/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.packageDependencies;

import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.ComplementPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;

public class DependencyRule {
  private NamedScope myFromScope;
  private NamedScope myToScope;
  private boolean myDenyRule = true;

  public DependencyRule(NamedScope fromPackageSet, NamedScope toPackageSet, boolean isDenyRule) {
    myFromScope = fromPackageSet;
    myToScope = toPackageSet;
    myDenyRule = isDenyRule;
  }

  public boolean isForbiddenToUse(PsiFile from, PsiFile to) {
    if (myFromScope == null || myToScope == null) return false;
    DependencyValidationManager holder = DependencyValidationManager.getInstance(from.getProject());
    return (myDenyRule
            ? myFromScope.getValue().contains(from, holder)
            : new ComplementPackageSet(myFromScope.getValue()).contains(from, holder)) &&
           myToScope.getValue().contains(to, holder);
  }

  public String getDisplayText() {
    StringBuffer buf = new StringBuffer();
    buf.append(myDenyRule ? "Deny " : "Allow ");
    buf.append("usages of scope '");
    if (myToScope != null) {
      buf.append(myToScope.getName());
    }
    buf.append("' " + (myDenyRule ? " " : "only ") + "in scope '");
    if (myFromScope != null) {
      buf.append(myFromScope.getName());
    }
    buf.append('\'');
    return buf.toString();
  }

  public boolean equals(Object o) {
    if (!(o instanceof DependencyRule)) return false;
    DependencyRule rule = (DependencyRule)o;
    return getDisplayText().equals(rule.getDisplayText());
  }

  public int hashCode() {
    return getDisplayText().hashCode();
  }

  public DependencyRule createCopy() {
    return new DependencyRule(myFromScope == null ? null : myFromScope.createCopy(),
                              myToScope == null ? null : myToScope.createCopy(),
                              myDenyRule);
  }

  public boolean isDenyRule() {
    return myDenyRule;
  }

  public NamedScope getFromScope() {
    return myFromScope;
  }

  public void setFromScope(NamedScope fromScope) {
    myFromScope = fromScope;
  }

  public NamedScope getToScope() {
    return myToScope;
  }

  public void setToScope(NamedScope toScope) {
    myToScope = toScope;
  }
}