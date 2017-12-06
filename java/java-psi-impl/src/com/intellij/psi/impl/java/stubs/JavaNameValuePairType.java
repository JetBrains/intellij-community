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
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.java.stubs.impl.PsiNameValuePairStubImpl;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.java.NameValuePairElement;
import com.intellij.psi.impl.source.tree.java.PsiNameValuePairImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class JavaNameValuePairType extends JavaStubElementType<PsiNameValuePairStub, PsiNameValuePair> {

  protected JavaNameValuePairType() {
    super("NAME_VALUE_PAIR", true);
  }

  @Override
  public PsiNameValuePair createPsi(@NotNull ASTNode node) {
    return new PsiNameValuePairImpl(node);
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new NameValuePairElement();
  }

  @Override
  public PsiNameValuePairStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    String name = null;
    String value = null;
    List<LighterASTNode> children = tree.getChildren(node);
    for (LighterASTNode child : children) {
      if (child.getTokenType() == JavaTokenType.IDENTIFIER) {
        name = RecordUtil.intern(tree.getCharTable(), child);
      }
      else if (ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET.contains(child.getTokenType())) {
        value = LightTreeUtil.toFilteredString(tree, child, null);
      }
    }
    return new PsiNameValuePairStubImpl(parentStub, name, value);
  }

  @Override
  public PsiNameValuePair createPsi(@NotNull PsiNameValuePairStub stub) {
    return getPsiFactory(stub).createNameValuePair(stub);
  }

  @Override
  public void serialize(@NotNull PsiNameValuePairStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());

    String value = stub.getValue();
    boolean hasValue = value != null;
    dataStream.writeBoolean(hasValue);
    if (hasValue) {
      dataStream.writeUTFFast(value);
    }
  }

  @NotNull
  @Override
  public PsiNameValuePairStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    boolean hasValue = dataStream.readBoolean();
    return new PsiNameValuePairStubImpl(parentStub, name, hasValue ? dataStream.readUTFFast() : null);
  }

  @Override
  public void indexStub(@NotNull PsiNameValuePairStub stub, @NotNull IndexSink sink) {
  }
}
