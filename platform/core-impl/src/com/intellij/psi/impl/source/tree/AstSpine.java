// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.impl.source.StubbedSpine;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class AstSpine implements StubbedSpine {
  static final AstSpine EMPTY_SPINE = new AstSpine(Collections.emptyList()); 
  private final List<CompositeElement> myNodes;

  AstSpine(@NotNull List<CompositeElement> nodes) {
    myNodes = nodes;
  }

  @Override
  public int getStubCount() {
    return myNodes.size();
  }

  @Override
  public @Nullable PsiElement getStubPsi(int index) {
    return index >= myNodes.size() ? null : myNodes.get(index).getPsi();
  }

  public int getStubIndex(@NotNull StubBasedPsiElement psi) {
    return myNodes.indexOf((CompositeElement)psi.getNode());
  }

  @Override
  public @Nullable IElementType getStubType(int index) {
    return index >= myNodes.size() ? null : myNodes.get(index).getElementType();
  }

  public @NotNull List<CompositeElement> getSpineNodes() {
    return myNodes;
  }
}
