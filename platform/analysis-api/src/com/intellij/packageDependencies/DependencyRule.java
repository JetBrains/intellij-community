// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.ComplementPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DependencyRule {
  private NamedScope myFromScope;
  private NamedScope myToScope;
  private final boolean myDenyRule;

  public DependencyRule(NamedScope fromPackageSet, NamedScope toPackageSet, boolean isDenyRule) {
    myFromScope = fromPackageSet;
    myToScope = toPackageSet;
    myDenyRule = isDenyRule;
  }

  public boolean isForbiddenToUse(@NotNull PsiFile from, @NotNull PsiFile to) {
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

  public boolean isApplicable(@NotNull PsiFile file){
    if (myFromScope == null || myToScope == null) return false;
    final PackageSet fromSet = myFromScope.getValue();
    if (fromSet == null) return false;

    DependencyValidationManager holder = DependencyValidationManager.getInstance(file.getProject());
    return myDenyRule
            ? fromSet.contains(file, holder)
            : new ComplementPackageSet(fromSet).contains(file, holder);
  }

  public String getDisplayText() {
    String toScopeName = myToScope == null ? "" : myToScope.getPresentableName();
    String fromScopeName = myFromScope == null ? "" : myFromScope.getPresentableName();

    return myDenyRule
           ? AnalysisBundle.message("scope.display.name.deny.scope", toScopeName, fromScopeName)
           : AnalysisBundle.message("scope.display.name.allow.scope", toScopeName, fromScopeName);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DependencyRule other)) return false;
    return getDisplayText().equals(other.getDisplayText()) &&
           Comparing.strEqual(getPackageSetPresentation(myFromScope), getPackageSetPresentation(other.myFromScope)) &&
           Comparing.strEqual(getPackageSetPresentation(myToScope), getPackageSetPresentation(other.myToScope));

  }

  private static @Nullable String getPackageSetPresentation(NamedScope scope) {
    if (scope != null) {
      final PackageSet packageSet = scope.getValue();
      if (packageSet != null) {
        return packageSet.getText();
      }
    }
    return null;
  }

  @Override
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