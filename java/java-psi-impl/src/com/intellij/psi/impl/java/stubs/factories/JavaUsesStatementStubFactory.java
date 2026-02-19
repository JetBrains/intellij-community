// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiUsesStatement;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiUsesStatementStubImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaUsesStatementStubFactory implements LightStubElementFactory<PsiUsesStatementStubImpl, PsiUsesStatement> {
  @Override
  public @NotNull PsiUsesStatementStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    LighterASTNode ref = LightTreeUtil.firstChildOfType(tree, node, JavaElementType.JAVA_CODE_REFERENCE);
    String refText = ref != null ? JavaSourceUtil.getReferenceText(tree, ref) : null;
    return new PsiUsesStatementStubImpl(parentStub, refText);
  }

  @Override
  public PsiUsesStatement createPsi(@NotNull PsiUsesStatementStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createUsesStatement(stub);
  }
  
  @Override
  public @NotNull PsiUsesStatementStubImpl createStub(@NotNull PsiUsesStatement psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
}