/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.impl.source.tree.SourceUtil;
import com.intellij.psi.impl.source.tree.java.ImportStaticStatementElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.io.StringRef;
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
  public PsiImportStatementBase createPsi(final PsiImportStatementStub stub) {
    return getPsiFactory(stub).createImportStatement(stub);
  }

  @Override
  public PsiImportStatementBase createPsi(final ASTNode node) {
    if (node instanceof ImportStaticStatementElement) {
      return new PsiImportStaticStatementImpl(node);
    }
    else {
      return new PsiImportStatementImpl(node);
    }
  }

  @Override
  public PsiImportStatementStub createStub(final LighterAST tree,
                                           final LighterASTNode node,
                                           final StubElement parentStub) {
    boolean isOnDemand = false;
    String refText = null;

    for (final LighterASTNode child : tree.getChildren(node)) {
      final IElementType type = child.getTokenType();
      if (type == JavaElementType.JAVA_CODE_REFERENCE || type == JavaElementType.IMPORT_STATIC_REFERENCE) {
        refText = SourceUtil.getTextSkipWhiteSpaceAndComments(tree, child);
      }
      else if (type == JavaTokenType.ASTERISK) {
        isOnDemand = true;
      }
    }

    final byte flags = PsiImportStatementStubImpl.packFlags(isOnDemand, node.getTokenType() == JavaElementType.IMPORT_STATIC_STATEMENT);
    return new PsiImportStatementStubImpl(parentStub, refText, flags);
  }

  @Override
  public void serialize(final PsiImportStatementStub stub, final StubOutputStream dataStream) throws IOException {
    dataStream.writeByte(((PsiImportStatementStubImpl)stub).getFlags());
    dataStream.writeName(stub.getImportReferenceText());
  }

  @Override
  public PsiImportStatementStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    final byte flags = dataStream.readByte();
    final StringRef refText = dataStream.readName();
    return new PsiImportStatementStubImpl(parentStub, refText, flags);
  }

  @Override
  public void indexStub(final PsiImportStatementStub stub, final IndexSink sink) {
  }
}
