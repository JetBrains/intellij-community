// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.impl.PsiLiteralStub;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaLiteralExpressionStubFactory implements LightStubElementFactory<PsiLiteralStub, PsiLiteralExpression> {

  @Override
  public @NotNull PsiLiteralStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    return new PsiLiteralStub(parentStub, RecordUtil.intern(tree.getCharTable(), tree.getChildren(node).get(0)));
  }

  @Override
  public PsiLiteralExpression createPsi(@NotNull PsiLiteralStub stub) {
    return new PsiLiteralExpressionImpl(stub);
  }

  @Override
  public boolean shouldCreateStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement parentStub) {
    LighterASTNode parent = tree.getParent(node);
    return parent != null && parent.getTokenType() == JavaStubElementTypes.NAME_VALUE_PAIR;
  }

  @Override
  public boolean shouldCreateStub(ASTNode node) {
    return node.getTreeParent().getElementType() == JavaStubElementTypes.NAME_VALUE_PAIR;
  }

  @Override
  public @NotNull PsiLiteralStub createStub(@NotNull PsiLiteralExpression psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
}
