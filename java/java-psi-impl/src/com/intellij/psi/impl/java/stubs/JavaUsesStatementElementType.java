/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.PsiUsesStatement;
import com.intellij.psi.impl.java.stubs.impl.PsiUsesStatementStubImpl;
import com.intellij.psi.impl.source.PsiUsesStatementImpl;
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

public class JavaUsesStatementElementType extends JavaStubElementType<PsiUsesStatementStub, PsiUsesStatement> {
  public JavaUsesStatementElementType() {
    super("USES_STATEMENT");
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new CompositeElement(this);
  }

  @Override
  public PsiUsesStatement createPsi(@NotNull PsiUsesStatementStub stub) {
    return getPsiFactory(stub).createUsesStatement(stub);
  }

  @Override
  public PsiUsesStatement createPsi(@NotNull ASTNode node) {
    return new PsiUsesStatementImpl(node);
  }

  @Override
  public PsiUsesStatementStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    LighterASTNode ref = LightTreeUtil.firstChildOfType(tree, node, JavaElementType.JAVA_CODE_REFERENCE);
    String refText = ref != null ? JavaSourceUtil.getReferenceText(tree, ref) : null;
    return new PsiUsesStatementStubImpl(parentStub, refText);
  }

  @Override
  public void serialize(@NotNull PsiUsesStatementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getClassName());
  }

  @NotNull
  @Override
  public PsiUsesStatementStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PsiUsesStatementStubImpl(parentStub, dataStream.readNameString());
  }

  @Override
  public void indexStub(@NotNull PsiUsesStatementStub stub, @NotNull IndexSink sink) { }
}