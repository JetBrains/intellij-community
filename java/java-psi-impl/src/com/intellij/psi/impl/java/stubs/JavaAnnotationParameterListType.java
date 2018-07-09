/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.impl.java.stubs.impl.PsiAnnotationParameterListStubImpl;
import com.intellij.psi.impl.source.tree.java.AnnotationParamListElement;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationParamListImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author Dmitry Avdeev
 */
public class JavaAnnotationParameterListType extends JavaStubElementType<PsiAnnotationParameterListStub, PsiAnnotationParameterList> {

  protected JavaAnnotationParameterListType() {
    super("ANNOTATION_PARAMETER_LIST", true);
  }

  @Override
  public PsiAnnotationParameterList createPsi(@NotNull ASTNode node) {
    return new PsiAnnotationParamListImpl(node);
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new AnnotationParamListElement();
  }

  @Override
  public PsiAnnotationParameterListStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    return new PsiAnnotationParameterListStubImpl(parentStub);
  }

  @Override
  public PsiAnnotationParameterList createPsi(@NotNull PsiAnnotationParameterListStub stub) {
    return getPsiFactory(stub).createAnnotationParameterList(stub);
  }

  @Override
  public void serialize(@NotNull PsiAnnotationParameterListStub stub, @NotNull StubOutputStream dataStream) throws IOException {
  }

  @NotNull
  @Override
  public PsiAnnotationParameterListStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PsiAnnotationParameterListStubImpl(parentStub);
  }

  @Override
  public void indexStub(@NotNull PsiAnnotationParameterListStub stub, @NotNull IndexSink sink) {
  }
}
