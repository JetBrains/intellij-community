// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.stubs.StubTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class SpineRef extends SubstrateRef {
  private final PsiFileImpl myFile;
  private final int myIndex;

  SpineRef(@NotNull PsiFileImpl file, int index) {
    myFile = file;
    myIndex = index;
  }

  @Override
  public @NotNull ASTNode getNode() {
    return myFile.calcTreeElement().getStubbedSpine().getSpineNodes().get(myIndex);
  }

  @Override
  public @Nullable Stub getStub() {
    StubTree tree = myFile.getStubTree();
    return tree == null ? null : tree.getPlainList().get(myIndex);
  }

  @Override
  public @Nullable Stub getGreenStub() {
    StubTree tree = myFile.getGreenStubTree();
    return tree == null ? null : tree.getPlainList().get(myIndex);
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public @NotNull PsiFile getContainingFile() {
    return myFile;
  }
}
