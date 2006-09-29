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

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.ComplementPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.Nullable;

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
    final PackageSet fromSet = myFromScope.getValue();
    final PackageSet toSet = myToScope.getValue();
    if (fromSet == null || toSet == null) return false;
    DependencyValidationManager holder = DependencyValidationManager.getInstance(from.getProject());
    return (myDenyRule
            ? fromSet.contains(from, holder)
            : new ComplementPackageSet(fromSet).contains(from, holder))
           && toSet.contains(to, holder);
  }

  public boolean isApplicable(PsiFile file){
    if (myFromScope == null || myToScope == null) return false;
    final PackageSet fromSet = myFromScope.getValue();
    if (fromSet == null) return false;

    DependencyValidationManager holder = DependencyValidationManager.getInstance(file.getProject());
    return (myDenyRule
            ? fromSet.contains(file, holder)
            : new ComplementPackageSet(fromSet).contains(file, holder));
  }

  public String getDisplayText() {
    String toScopeName = myToScope == null ? "" : myToScope.getName();
    String fromScopeName = myFromScope == null ? "" : myFromScope.getName();

    return myDenyRule
           ? AnalysisScopeBundle.message("scope.display.name.deny.scope", toScopeName, fromScopeName)
           : AnalysisScopeBundle.message("scope.display.name.allow.scope", toScopeName, fromScopeName);
  }

  public boolean equals(Object o) {
    if (!(o instanceof DependencyRule)) return false;
    DependencyRule other = (DependencyRule)o;
    return getDisplayText().equals(other.getDisplayText()) &&
           Comparing.strEqual(getPackageSetPresentation(myFromScope), getPackageSetPresentation(other.myFromScope)) &&
           Comparing.strEqual(getPackageSetPresentation(myToScope), getPackageSetPresentation(other.myToScope));

  }

  @Nullable
  private static String getPackageSetPresentation(NamedScope scope) {
    if (scope != null) {
      final PackageSet packageSet = scope.getValue();
      if (packageSet != null) {
        return packageSet.getText();
      }
    }
    return null;
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