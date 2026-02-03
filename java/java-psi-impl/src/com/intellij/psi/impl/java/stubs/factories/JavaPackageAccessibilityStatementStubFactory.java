// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiPackageAccessibilityStatementStubImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavaPackageAccessibilityStatementStubFactory implements LightStubElementFactory<PsiPackageAccessibilityStatementStubImpl, PsiPackageAccessibilityStatement> {
  @Override
  public @NotNull PsiPackageAccessibilityStatementStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    String refText = null;
    List<String> to = new SmartList<>();

    for (LighterASTNode child : tree.getChildren(node)) {
      IElementType type = child.getTokenType();
      if (type == JavaElementType.JAVA_CODE_REFERENCE) refText = JavaSourceUtil.getReferenceText(tree, child);
      else if (type == JavaElementType.MODULE_REFERENCE) to.add(JavaSourceUtil.getReferenceText(tree, child));
    }

    return new PsiPackageAccessibilityStatementStubImpl(parentStub, (IJavaElementType)node.getTokenType(), refText, to);
  }

  @Override
  public PsiPackageAccessibilityStatement createPsi(@NotNull PsiPackageAccessibilityStatementStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createPackageAccessibilityStatement(stub);
  }
  
  @Override
  public @NotNull PsiPackageAccessibilityStatementStubImpl createStub(@NotNull PsiPackageAccessibilityStatement psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
}