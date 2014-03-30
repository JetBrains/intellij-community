/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.impl.source.PsiReceiverParameterImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author max
 */
public abstract class JavaParameterElementType extends JavaStubElementType<PsiParameterStub, PsiParameter> {
  public static final TokenSet ID_TYPES = TokenSet.create(JavaTokenType.IDENTIFIER, JavaTokenType.THIS_KEYWORD);

  public JavaParameterElementType(@NotNull String id) {
    super(id);
  }

  @Override
  public PsiParameter createPsi(@NotNull PsiParameterStub stub) {
    return getPsiFactory(stub).createParameter(stub);
  }

  @Override
  public PsiParameter createPsi(@NotNull ASTNode node) {
    boolean receiver = node.getElementType() == JavaElementType.RECEIVER_PARAMETER;
    return receiver ? new PsiReceiverParameterImpl(node) : new PsiParameterImpl(node);
  }

  @Override
  public PsiParameterStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    TypeInfo typeInfo = TypeInfo.create(tree, node, parentStub);
    LighterASTNode id = LightTreeUtil.requiredChildOfType(tree, node, ID_TYPES);
    String name = RecordUtil.intern(tree.getCharTable(), id);
    return new PsiParameterStubImpl(parentStub, name, typeInfo, typeInfo.isEllipsis);
  }

  @Override
  public void serialize(@NotNull PsiParameterStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    TypeInfo.writeTYPE(dataStream, stub.getType(false));
    dataStream.writeBoolean(stub.isParameterTypeEllipsis());
  }

  @NotNull
  @Override
  public PsiParameterStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    StringRef name = dataStream.readName();
    TypeInfo type = TypeInfo.readTYPE(dataStream);
    boolean isEllipsis = dataStream.readBoolean();
    return new PsiParameterStubImpl(parentStub, name, type, isEllipsis);
  }

  @Override
  public void indexStub(@NotNull PsiParameterStub stub, @NotNull IndexSink sink) {
  }
}
