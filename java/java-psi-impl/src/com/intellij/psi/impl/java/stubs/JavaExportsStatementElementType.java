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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiExportsStatement;
import com.intellij.psi.impl.java.stubs.impl.PsiExportsStatementStubImpl;
import com.intellij.psi.impl.source.PsiExportsStatementImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

import static com.intellij.util.ObjectUtils.notNull;

public class JavaExportsStatementElementType extends JavaStubElementType<PsiExportsStatementStub, PsiExportsStatement> {
  public JavaExportsStatementElementType() {
    super("EXPORTS_STATEMENT");
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new CompositeElement(this);
  }

  @Override
  public PsiExportsStatement createPsi(@NotNull PsiExportsStatementStub stub) {
    return getPsiFactory(stub).createExportsStatement(stub);
  }

  @Override
  public PsiExportsStatement createPsi(@NotNull ASTNode node) {
    return new PsiExportsStatementImpl(node);
  }

  @Override
  public PsiExportsStatementStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    String refText = null;
    List<String> to = ContainerUtil.newSmartList();

    for (LighterASTNode child : tree.getChildren(node)) {
      IElementType type = child.getTokenType();
      if (type == JavaElementType.JAVA_CODE_REFERENCE) refText = JavaSourceUtil.getReferenceText(tree, child);
      else if (type == JavaElementType.MODULE_REFERENCE) to.add(JavaSourceUtil.getReferenceText(tree, child));
    }

    return new PsiExportsStatementStubImpl(parentStub, notNull(refText, ""), to);
  }

  @Override
  public void serialize(@NotNull PsiExportsStatementStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeUTFFast(stub.getPackageName());
    dataStream.writeUTFFast(StringUtil.join(stub.getTargets(), "/"));
  }

  @NotNull
  @Override
  public PsiExportsStatementStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String packageName = dataStream.readUTFFast();
    List<String> targets = StringUtil.split(dataStream.readUTFFast(), "/");
    return new PsiExportsStatementStubImpl(parentStub, packageName, targets);
  }

  @Override
  public void indexStub(@NotNull PsiExportsStatementStub stub, @NotNull IndexSink sink) { }
}