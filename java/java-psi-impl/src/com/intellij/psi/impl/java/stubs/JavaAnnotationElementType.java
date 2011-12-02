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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.impl.java.stubs.impl.PsiAnnotationStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.java.AnnotationElement;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author max
 */
public class JavaAnnotationElementType extends JavaStubElementType<PsiAnnotationStub, PsiAnnotation> {
  public JavaAnnotationElementType() {
    super("ANNOTATION");
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new AnnotationElement();
  }

  @Override
  public PsiAnnotation createPsi(@NotNull final PsiAnnotationStub stub) {
    return getPsiFactory(stub).createAnnotation(stub);
  }

  @Override
  public PsiAnnotation createPsi(@NotNull final ASTNode node) {
    return new PsiAnnotationImpl(node);
  }  

  @Override
  public PsiAnnotationStub createStub(final LighterAST tree, final LighterASTNode node, final StubElement parentStub) {
    final String text = LightTreeUtil.toFilteredString(tree, node, null);
    return new PsiAnnotationStubImpl(parentStub, text);
  }

  @Override
  public void serialize(final PsiAnnotationStub stub, final StubOutputStream dataStream) throws IOException {
    dataStream.writeUTFFast(stub.getText());
  }

  @Override
  public PsiAnnotationStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PsiAnnotationStubImpl(parentStub, dataStream.readUTFFast());
  }

  @Override
  public void indexStub(final PsiAnnotationStub stub, final IndexSink sink) {
    final String refText = getReferenceShortName(stub.getText());
    sink.occurrence(JavaStubIndexKeys.ANNOTATIONS, refText);
  }

  private static String getReferenceShortName(String annotationText) {
    final int index = annotationText.indexOf('('); //to get the text of reference itself
    if (index >= 0) {
      return PsiNameHelper.getShortClassName(annotationText.substring(0, index));
    }
    return PsiNameHelper.getShortClassName(annotationText);
  }
}
