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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.impl.PsiParameterStubImpl;
import com.intellij.psi.impl.source.PsiParameterImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.java.ParameterElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author max
 */
public class JavaParameterElementType extends JavaStubElementType<PsiParameterStub, PsiParameter> {
  public JavaParameterElementType() {
    super("PARAMETER");
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new ParameterElement(JavaElementType.PARAMETER);
  }

  @Override
  public PsiParameter createPsi(@NotNull PsiParameterStub stub) {
    return getPsiFactory(stub).createParameter(stub);
  }

  @Override
  public PsiParameter createPsi(@NotNull ASTNode node) {
    return new PsiParameterImpl(node);
  }

  @Override
  public PsiParameterStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    TypeInfo typeInfo = TypeInfo.create(tree, node, parentStub);
    LighterASTNode id = LightTreeUtil.requiredChildOfType(tree, node, JavaTokenType.IDENTIFIER);
    String name = RecordUtil.intern(tree.getCharTable(), id);
    return new PsiParameterStubImpl(parentStub, name, typeInfo, typeInfo.isEllipsis, false);
  }

  @Override
  public void serialize(@NotNull PsiParameterStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    TypeInfo.writeTYPE(dataStream, stub.getType(false));
    dataStream.writeByte(((PsiParameterStubImpl)stub).getFlags());
  }

  @NotNull
  @Override
  public PsiParameterStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = dataStream.readNameString();
    if (name == null) throw new IOException("corrupted indices");
    TypeInfo type = TypeInfo.readTYPE(dataStream);
    byte flags = dataStream.readByte();
    return new PsiParameterStubImpl(parentStub, name, type, flags);
  }

  @Override
  public void indexStub(@NotNull PsiParameterStub stub, @NotNull IndexSink sink) { }
}