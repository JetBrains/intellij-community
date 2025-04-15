// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiImportStatementStubImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaImportStatementStubFactory implements LightStubElementFactory<PsiImportStatementStubImpl, PsiImportStatementBase> {
  @Override
  public @NotNull PsiImportStatementStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    boolean isOnDemand = false;
    String refText = null;

    for (LighterASTNode child : tree.getChildren(node)) {
      IElementType type = child.getTokenType();
      if (type == JavaElementType.JAVA_CODE_REFERENCE ||
          type == JavaElementType.IMPORT_STATIC_REFERENCE ||
          type == JavaElementType.MODULE_REFERENCE) {
        refText = JavaSourceUtil.getReferenceText(tree, child);
      }
      else if (type == JavaTokenType.DOT || type == JavaTokenType.MODULE_KEYWORD) {
        isOnDemand = true;
      }
    }
    byte flags = PsiImportStatementStubImpl.packFlags(isOnDemand, node.getTokenType() == JavaElementType.IMPORT_STATIC_STATEMENT,
                                                      node.getTokenType() == JavaElementType.IMPORT_MODULE_STATEMENT);
    return new PsiImportStatementStubImpl(parentStub, refText, flags);
  }

  @Override
  public PsiImportStatementBase createPsi(@NotNull PsiImportStatementStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createImportStatement(stub);
  }
  
  @Override
  public @NotNull PsiImportStatementStubImpl createStub(@NotNull PsiImportStatementBase psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
}