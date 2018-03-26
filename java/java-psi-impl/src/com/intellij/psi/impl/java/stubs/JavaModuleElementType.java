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
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaModuleStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
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
    super("MODULE");
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
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
  public PsiJavaModuleStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    LighterASTNode ref = LightTreeUtil.requiredChildOfType(tree, node, JavaElementType.MODULE_REFERENCE);
    return new PsiJavaModuleStubImpl(parentStub, JavaSourceUtil.getReferenceText(tree, ref));
  }

  @Override
  public void serialize(@NotNull PsiJavaModuleStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
  }

  @NotNull
  @Override
  public PsiJavaModuleStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PsiJavaModuleStubImpl(parentStub, dataStream.readNameString());
  }

  @Override
  public void indexStub(@NotNull PsiJavaModuleStub stub, @NotNull IndexSink sink) {
    sink.occurrence(JavaStubIndexKeys.MODULE_NAMES, stub.getName());
  }
}