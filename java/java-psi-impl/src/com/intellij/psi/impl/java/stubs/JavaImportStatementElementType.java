// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.impl.java.stubs.impl.PsiImportStatementStubImpl;
import com.intellij.psi.impl.source.PsiImportModuleStatementImpl;
import com.intellij.psi.impl.source.PsiImportStatementImpl;
import com.intellij.psi.impl.source.PsiImportStaticStatementImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.impl.source.tree.java.ImportModuleStatementElement;
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
  public JavaImportStatementElementType(final @NonNls @NotNull String id, @NotNull IElementType parentElementType) {
    super(id, parentElementType);
  }

  @Override
  public PsiImportStatementBase createPsi(final @NotNull PsiImportStatementStub stub) {
    return getPsiFactory(stub).createImportStatement(stub);
  }

  @Override
  public PsiImportStatementBase createPsi(final @NotNull ASTNode node) {
    if (node instanceof ImportStaticStatementElement) {
      return new PsiImportStaticStatementImpl(node);
    }
    else if (node instanceof ImportModuleStatementElement) {
      return new PsiImportModuleStatementImpl(node);
    }
    else {
      return new PsiImportStatementImpl(node);
    }
  }

  @Override
  public @NotNull PsiImportStatementStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
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
  public void serialize(final @NotNull PsiImportStatementStub stub, final @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeByte(((PsiImportStatementStubImpl)stub).getFlags());
    dataStream.writeName(stub.getImportReferenceText());
  }

  @Override
  public @NotNull PsiImportStatementStub deserialize(final @NotNull StubInputStream dataStream, final StubElement parentStub) throws IOException {
    byte flags = dataStream.readByte();
    String refText = dataStream.readNameString();
    return new PsiImportStatementStubImpl(parentStub, refText, flags);
  }

  @Override
  public void indexStub(final @NotNull PsiImportStatementStub stub, final @NotNull IndexSink sink) {
  }
}
