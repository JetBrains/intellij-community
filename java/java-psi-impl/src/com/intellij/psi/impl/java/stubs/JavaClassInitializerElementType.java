// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.impl.java.stubs.impl.PsiClassInitializerStubImpl;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.PsiClassInitializerImpl;
import com.intellij.psi.impl.source.tree.java.ClassInitializerElement;
import com.intellij.psi.stubs.EmptyStubSerializer;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

public class JavaClassInitializerElementType extends JavaStubElementType<PsiClassInitializerStub, PsiClassInitializer>
  implements EmptyStubSerializer<PsiClassInitializerStub> {
  public JavaClassInitializerElementType() {
    super("CLASS_INITIALIZER", BasicJavaElementType.BASIC_CLASS_INITIALIZER);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new ClassInitializerElement();
  }

  @Override
  public PsiClassInitializer createPsi(final @NotNull PsiClassInitializerStub stub) {
    return getPsiFactory(stub).createClassInitializer(stub);
  }

  @Override
  public PsiClassInitializer createPsi(final @NotNull ASTNode node) {
    return new PsiClassInitializerImpl(node);
  }

  @Override
  public @NotNull PsiClassInitializerStub createStub(final @NotNull LighterAST tree,
                                                     final @NotNull LighterASTNode node,
                                                     final @NotNull StubElement<?> parentStub) {
    return new PsiClassInitializerStubImpl(parentStub);
  }

  @Override
  public @NotNull PsiClassInitializerStub instantiate(StubElement<?> parentStub) {
    return new PsiClassInitializerStubImpl(parentStub);
  }

  @Override
  public void indexStub(final @NotNull PsiClassInitializerStub stub, final @NotNull IndexSink sink) {
  }
}
