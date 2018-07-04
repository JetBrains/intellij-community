/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author max
 */
public abstract class JavaImportStatementElementType extends JavaStubElementType<PsiImportStatementStub, PsiImportStatementBase> {
  public JavaImportStatementElementType(@NonNls @NotNull final String id) {
    super(id);
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

  @Override
  public PsiImportStatementStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
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
