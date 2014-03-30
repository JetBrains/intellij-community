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
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.java.stubs.impl.PsiParameterListStubImpl;
import com.intellij.psi.impl.source.PsiParameterListImpl;
import com.intellij.psi.impl.source.tree.java.ParameterListElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author max
 */
public class JavaParameterListElementType extends JavaStubElementType<PsiParameterListStub, PsiParameterList> {
  public JavaParameterListElementType() {
    super("PARAMETER_LIST");
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new ParameterListElement();
  }

  @Override
  public PsiParameterList createPsi(@NotNull final PsiParameterListStub stub) {
    return getPsiFactory(stub).createParameterList(stub);
  }

  @Override
  public PsiParameterList createPsi(@NotNull final ASTNode node) {
    return new PsiParameterListImpl(node);
  }

  @Override
  public PsiParameterListStub createStub(final LighterAST tree, final LighterASTNode node, final StubElement parentStub) {
    return new PsiParameterListStubImpl(parentStub);
  }

  @Override
  public void serialize(@NotNull final PsiParameterListStub stub, @NotNull final StubOutputStream dataStream) throws IOException {
  }

  @NotNull
  @Override
  public PsiParameterListStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PsiParameterListStubImpl(parentStub);
  }

  @Override
  public void indexStub(@NotNull final PsiParameterListStub stub, @NotNull final IndexSink sink) {
  }
}
