// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.stubs.StubTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
class SpineRef extends SubstrateRef {
  private final PsiFileImpl myFile;
  private final int myIndex;

  SpineRef(@NotNull PsiFileImpl file, int index) {
    myFile = file;
    myIndex = index;
  }

  @NotNull
  @Override
  public ASTNode getNode() {
    return myFile.calcTreeElement().getStubbedSpine().getSpineNodes().get(myIndex);
  }

  @Nullable
  @Override
  public Stub getStub() {
    StubTree tree = myFile.getStubTree();
    return tree == null ? null : tree.getPlainList().get(myIndex);
  }

  @Nullable
  @Override
  public Stub getGreenStub() {
    StubTree tree = myFile.getGreenStubTree();
    return tree == null ? null : tree.getPlainList().get(myIndex);
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @NotNull
  @Override
  public PsiFile getContainingFile() {
    return myFile;
  }
}
