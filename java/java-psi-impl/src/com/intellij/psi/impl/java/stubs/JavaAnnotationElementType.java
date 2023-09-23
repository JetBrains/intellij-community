// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.impl.java.stubs.impl.PsiAnnotationStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.java.AnnotationElement;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.psi.stubs.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class JavaAnnotationElementType extends JavaStubElementType<PsiAnnotationStub, PsiAnnotation> {
  public JavaAnnotationElementType() {
    super("ANNOTATION", BasicJavaElementType.BASIC_ANNOTATION);
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new AnnotationElement();
  }

  @Override
  public PsiAnnotation createPsi(@NotNull PsiAnnotationStub stub) {
    return getPsiFactory(stub).createAnnotation(stub);
  }

  @Override
  public PsiAnnotation createPsi(@NotNull ASTNode node) {
    return new PsiAnnotationImpl(node);
  }

  @NotNull
  @Override
  public PsiAnnotationStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    String text = LightTreeUtil.toFilteredString(tree, node, null);
    return new PsiAnnotationStubImpl(parentStub, text);
  }

  @Override
  public void serialize(@NotNull PsiAnnotationStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeUTFFast(stub.getText());
  }

  @NotNull
  @Override
  public PsiAnnotationStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PsiAnnotationStubImpl(parentStub, dataStream.readUTFFast());
  }

  @Override
  public void indexStub(@NotNull PsiAnnotationStub stub, @NotNull IndexSink sink) {
    String shortName = getReferenceShortName(stub.getText());
    if (!StringUtil.isEmptyOrSpaces(shortName)) {
      sink.occurrence(JavaStubIndexKeys.ANNOTATIONS, shortName);
    }
  }

  private static String getReferenceShortName(String annotationText) {
    int index = annotationText.indexOf('(');
    if (index >= 0) annotationText = annotationText.substring(0, index);
    return PsiNameHelper.getShortClassName(annotationText);
  }

  @Override
  public boolean isAlwaysLeaf(StubBase<?> root) {
    return root instanceof PsiJavaFileStub && ((PsiJavaFileStub)root).isCompiled();
  }
}
