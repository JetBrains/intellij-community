// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.java.IJavaElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * keeping for backward compatibility
 */
@ApiStatus.Obsolete
public abstract class JavaStubElementType extends IJavaElementType {
  @ApiStatus.Internal
  protected JavaStubElementType(String debugName) {
    super(debugName);
  }

  @ApiStatus.Internal
  protected JavaStubElementType(String debugName, boolean leftBound) {
    super(debugName, leftBound);
  }

  @ApiStatus.Internal
  public static boolean isCompiled(StubElement<?> stub) {
    return getFileStub(stub).isCompiled();
  }

  @ApiStatus.Internal
  public static PsiJavaFileStub getFileStub(StubElement<?> stub) {
    StubElement<?> parent = stub;
    while (!(parent instanceof PsiFileStub)) {
      parent = parent.getParentStub();
    }

    return (PsiJavaFileStub)parent;
  }

  public PsiElement createPsi(@NotNull ASTNode node) {
    // backward compatibility with manifold.ij plugin
    return node.getPsi();
  }
}
