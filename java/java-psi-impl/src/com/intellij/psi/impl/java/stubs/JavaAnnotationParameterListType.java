// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.impl.java.stubs.impl.PsiAnnotationParameterListStubImpl;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.tree.java.AnnotationParamListElement;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationParamListImpl;
import com.intellij.psi.stubs.EmptyStubSerializer;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class JavaAnnotationParameterListType extends JavaStubElementType<PsiAnnotationParameterListStub, PsiAnnotationParameterList>
  implements EmptyStubSerializer<PsiAnnotationParameterListStub> {

  protected JavaAnnotationParameterListType() {
    super("ANNOTATION_PARAMETER_LIST", true, BasicJavaElementType.BASIC_ANNOTATION_PARAMETER_LIST);
  }

  @Override
  public PsiAnnotationParameterList createPsi(@NotNull ASTNode node) {
    return new PsiAnnotationParamListImpl(node);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new AnnotationParamListElement();
  }

  @Override
  public @NotNull PsiAnnotationParameterListStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    return new PsiAnnotationParameterListStubImpl(parentStub);
  }

  @Override
  public PsiAnnotationParameterList createPsi(@NotNull PsiAnnotationParameterListStub stub) {
    return getPsiFactory(stub).createAnnotationParameterList(stub);
  }

  @Override
  public @NotNull PsiAnnotationParameterListStub instantiate(StubElement<?> parentStub) {
    return new PsiAnnotationParameterListStubImpl(parentStub);
  }

  @Override
  public void indexStub(@NotNull PsiAnnotationParameterListStub stub, @NotNull IndexSink sink) {
  }
}
