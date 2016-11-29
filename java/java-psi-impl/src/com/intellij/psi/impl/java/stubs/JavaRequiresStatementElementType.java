/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.PsiRequiresStatement;
import com.intellij.psi.impl.java.stubs.impl.PsiRequiresStatementStubImpl;
import com.intellij.psi.impl.source.PsiRequiresStatementImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static com.intellij.util.ObjectUtils.notNull;

public class JavaRequiresStatementElementType extends JavaStubElementType<PsiRequiresStatementStub, PsiRequiresStatement> {
  public JavaRequiresStatementElementType() {
    super("REQUIRES_STATEMENT");
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new CompositeElement(this);
  }

  @Override
  public PsiRequiresStatement createPsi(@NotNull PsiRequiresStatementStub stub) {
    return getPsiFactory(stub).createRequiresStatement(stub);
  }

  @Override
  public PsiRequiresStatement createPsi(@NotNull ASTNode node) {
    return new PsiRequiresStatementImpl(node);
  }

  @Override
  public PsiRequiresStatementStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    String refText = null;
    boolean isPublic = false, isStatic = false;

    for (LighterASTNode child : tree.getChildren(node)) {
      IElementType type = child.getTokenType();
      if (type == JavaElementType.MODULE_REFERENCE) refText = JavaSourceUtil.getReferenceText(tree, child);
      else if (type == JavaTokenType.PUBLIC_KEYWORD) isPublic = true;
      else if (type == JavaTokenType.STATIC_KEYWORD) isStatic = true;
    }

    return new PsiRequiresStatementStubImpl(parentStub, notNull(refText, ""), isPublic, isStatic);
  }

  @Override
  public void serialize(@NotNull PsiRequiresStatementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getModuleName());
    dataStream.writeByte(stub.getFlags());
  }

  @NotNull
  @Override
  public PsiRequiresStatementStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    byte flags = dataStream.readByte();
    return new PsiRequiresStatementStubImpl(parentStub, name, flags);
  }

  @Override
  public void indexStub(@NotNull PsiRequiresStatementStub stub, @NotNull IndexSink sink) { }
}