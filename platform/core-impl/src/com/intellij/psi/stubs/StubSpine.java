// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.StubbedSpine;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class StubSpine implements StubbedSpine {
  private final StubTree myTree;

  StubSpine(@NotNull StubTree tree) {
    myTree = tree;
  }

  @Override
  public int getStubCount() {
    return myTree.getPlainList().size();
  }

  @Override
  public @Nullable PsiElement getStubPsi(int index) {
    List<StubElement<?>> stubs = myTree.getPlainList();
    return index >= stubs.size() ? null : stubs.get(index).getPsi();
  }

  @Override
  public @Nullable IElementType getStubType(int index) {
    List<StubElement<?>> stubs = myTree.getPlainList();
    return index >= stubs.size() ? null : stubs.get(index).getElementType();
  }
}
