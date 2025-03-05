// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaModuleStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.PsiJavaModuleImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class JavaModuleElementType extends JavaStubElementType<PsiJavaModuleStub, PsiJavaModule> {
  public JavaModuleElementType() {
    super("MODULE", BasicJavaElementType.BASIC_MODULE);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new CompositeElement(this);
  }

  @Override
  public PsiJavaModule createPsi(@NotNull PsiJavaModuleStub stub) {
    return getPsiFactory(stub).createModule(stub);
  }

  @Override
  public PsiJavaModule createPsi(@NotNull ASTNode node) {
    return new PsiJavaModuleImpl(node);
  }

  @Override
  public @NotNull PsiJavaModuleStub createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    LighterASTNode ref = LightTreeUtil.requiredChildOfType(tree, node, JavaElementType.MODULE_REFERENCE);
    return new PsiJavaModuleStubImpl(parentStub, JavaSourceUtil.getReferenceText(tree, ref), 0);
  }

  @Override
  public void serialize(@NotNull PsiJavaModuleStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeVarInt(stub.getResolution());
  }

  @Override
  public @NotNull PsiJavaModuleStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PsiJavaModuleStubImpl(parentStub, dataStream.readNameString(), dataStream.readVarInt());
  }

  @Override
  public void indexStub(@NotNull PsiJavaModuleStub stub, @NotNull IndexSink sink) {
    sink.occurrence(JavaStubIndexKeys.MODULE_NAMES, stub.getName());
  }
}