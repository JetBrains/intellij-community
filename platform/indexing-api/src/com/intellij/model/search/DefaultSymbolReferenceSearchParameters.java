// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

public class DefaultSymbolReferenceSearchParameters implements SymbolReferenceSearchParameters {

  private final Project myProject;
  private final Symbol myTarget;
  private final SearchScope myScope;
  private final boolean myIgnoreAccessScope;

  private final NotNullLazyValue<SearchScope> myEffectiveScope;

  public DefaultSymbolReferenceSearchParameters(@NotNull Project project,
                                                @NotNull Symbol target,
                                                @NotNull SearchScope scope,
                                                boolean ignoreAccessScope) {
    myProject = project;
    myTarget = target;
    myScope = scope;
    myIgnoreAccessScope = ignoreAccessScope;
    if (myIgnoreAccessScope || !(myTarget instanceof PsiElement)) {
      myEffectiveScope = NotNullLazyValue.createConstantValue(myScope);
    }
    else {
      myEffectiveScope = AtomicNotNullLazyValue.createValue(this::doGetEffectiveSearchScope);
    }
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public Symbol getTarget() {
    return myTarget;
  }

  @NotNull
  @Override
  public SearchScope getOriginalSearchScope() {
    return myScope;
  }

  @Override
  public boolean isIgnoreAccessScope() {
    return myIgnoreAccessScope;
  }

  @NotNull
  @Override
  public SearchScope getEffectiveSearchScope() {
    return myEffectiveScope.getValue();
  }

  @NotNull
  private SearchScope doGetEffectiveSearchScope() {
    return ReadAction.compute(() -> {
      SearchScope useScope = PsiSearchHelper.getInstance(myProject).getUseScope((PsiElement)myTarget);
      return myScope.intersectWith(useScope);
    });
  }
}
