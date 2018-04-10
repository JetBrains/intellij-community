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
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.java.stubs.impl.PsiTypeParameterStubImpl;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterImpl;
import com.intellij.psi.impl.source.tree.java.TypeParameterElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author max
 */
public class JavaTypeParameterElementType extends JavaStubElementType<PsiTypeParameterStub, PsiTypeParameter> {
  public JavaTypeParameterElementType() {
    super("TYPE_PARAMETER");
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new TypeParameterElement();
  }

  @Override
  public PsiTypeParameter createPsi(@NotNull final PsiTypeParameterStub stub) {
    return getPsiFactory(stub).createTypeParameter(stub);
  }

  @Override
  public PsiTypeParameter createPsi(@NotNull final ASTNode node) {
    return new PsiTypeParameterImpl(node);
  }

  @Override
  public PsiTypeParameterStub createStub(final LighterAST tree, final LighterASTNode node, final StubElement parentStub) {
    final LighterASTNode id = LightTreeUtil.requiredChildOfType(tree, node, JavaTokenType.IDENTIFIER);
    final String name = RecordUtil.intern(tree.getCharTable(), id);
    return new PsiTypeParameterStubImpl(parentStub, name);
  }

  @Override
  public void serialize(@NotNull final PsiTypeParameterStub stub, @NotNull final StubOutputStream dataStream) throws IOException {
    String name = stub.getName();
    dataStream.writeName(name);
  }

  @NotNull
  @Override
  public PsiTypeParameterStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PsiTypeParameterStubImpl(parentStub, dataStream.readNameString());
  }

  @Override
  public void indexStub(@NotNull final PsiTypeParameterStub stub, @NotNull final IndexSink sink) {
  }
}
