// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.impl.java.stubs.impl.PsiImportStatementStubImpl;
import com.intellij.psi.impl.source.PsiImportStatementImpl;
import com.intellij.psi.impl.source.PsiImportStaticStatementImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.impl.source.tree.java.ImportStaticStatementElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class JavaImportStatementElementType extends JavaStubElementType<PsiImportStatementStub, PsiImportStatementBase> {
  public JavaImportStatementElementType(@NonNls @NotNull final String id, @NotNull IElementType parentElementType) {
    super(id, parentElementType);
  }

  @Override
  public PsiImportStatementBase createPsi(@NotNull final PsiImportStatementStub stub) {
    return getPsiFactory(stub).createImportStatement(stub);
  }

  @Override
  public PsiImportStatementBase createPsi(@NotNull final ASTNode node) {
    if (node instanceof ImportStaticStatementElement) {
      return new PsiImportStaticStatementImpl(node);
    }
    else {
      return new PsiImportStatementImpl(node);
    }
  }

  @NotNull
  @Override
  public PsiImportStatementStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    boolean isOnDemand = false;
    String refText = null;

    for (LighterASTNode child : tree.getChildren(node)) {
      IElementType type = child.getTokenType();
      if (type == JavaElementType.JAVA_CODE_REFERENCE || type == JavaElementType.IMPORT_STATIC_REFERENCE) {
        refText = JavaSourceUtil.getReferenceText(tree, child);
      }
      else if (type == JavaTokenType.DOT) {
        isOnDemand = true;
      }
    }

    byte flags = PsiImportStatementStubImpl.packFlags(isOnDemand, node.getTokenType() == JavaElementType.IMPORT_STATIC_STATEMENT);
    return new PsiImportStatementStubImpl(parentStub, refText, flags);
  }

  @Override
  public void serialize(@NotNull final PsiImportStatementStub stub, @NotNull final StubOutputStream dataStream) throws IOException {
    dataStream.writeByte(((PsiImportStatementStubImpl)stub).getFlags());
    dataStream.writeName(stub.getImportReferenceText());
  }

  @NotNull
  @Override
  public PsiImportStatementStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    byte flags = dataStream.readByte();
    String refText = dataStream.readNameString();
    return new PsiImportStatementStubImpl(parentStub, refText, flags);
  }

  @Override
  public void indexStub(@NotNull final PsiImportStatementStub stub, @NotNull final IndexSink sink) {
  }
}
