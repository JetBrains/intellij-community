// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiPackageStatementStubImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaPackageStatementStubFactory implements LightStubElementFactory<PsiPackageStatementStubImpl, PsiPackageStatement> {
  @Override
  public @NotNull PsiPackageStatementStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    String refText = null;
    for (LighterASTNode child : tree.getChildren(node)) {
      IElementType type = child.getTokenType();
      if (type == JavaElementType.JAVA_CODE_REFERENCE) refText = JavaSourceUtil.getReferenceText(tree, child);
    }

    return new PsiPackageStatementStubImpl(parentStub, refText);
  }

  @Override
  public PsiPackageStatement createPsi(@NotNull PsiPackageStatementStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createPackageStatement(stub);
  }

  @Override
  public @NotNull PsiPackageStatementStubImpl createStub(@NotNull PsiPackageStatement psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
}