// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.url.parameters;

import com.intellij.ide.presentation.Presentation;
import com.intellij.microservices.utils.SimpleNamePomTarget;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Presentation(provider = PathVariablePresentationProvider.class)
public final class PathVariablePomTarget extends SimpleNamePomTarget {

  public PathVariablePomTarget(@NotNull String name,
                                PsiElement scope,
                                TextRange range,
                                SemDefinitionProvider semDefinitionProvider) {
    super(name);
    myScope = scope;
    myTextRange = range;
    mySemDefinitionProvider = semDefinitionProvider;
  }

  private final PsiElement myScope;
  private final TextRange myTextRange;
  private final SemDefinitionProvider mySemDefinitionProvider;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    PathVariablePomTarget target = (PathVariablePomTarget)o;
    return myScope.equals(target.myScope) &&
           myTextRange.equals(target.myTextRange);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myScope, myTextRange);
  }

  public PsiElement getScope() {
    return myScope;
  }

  public TextRange getTextRange() {
    return myTextRange;
  }

  public SemDefinitionProvider getSemDefinitionProvider() {
    return mySemDefinitionProvider;
  }

  //TODO: not sure if it really should be a method of this class
  @NotNull
  @ApiStatus.Internal
  public Iterable<PsiElement> findSemDefinitionPsiElement() {
    return mySemDefinitionProvider.findSemDefiningElements(this);
  }
}