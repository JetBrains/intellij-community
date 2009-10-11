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

/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.compiled.ClsParameterImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiParameterStubImpl;
import com.intellij.psi.impl.source.PsiParameterImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.io.StringRef;

import java.io.IOException;

public class JavaParameterElementType extends JavaStubElementType<PsiParameterStub, PsiParameter> {
  public JavaParameterElementType() {
    super("PARAMETER");
  }

  public PsiParameter createPsi(final PsiParameterStub stub) {
    if (isCompiled(stub)) {
      return new ClsParameterImpl(stub);
    }
    else {
      return new PsiParameterImpl(stub);
    }
  }

  public PsiParameter createPsi(final ASTNode node) {
    return new PsiParameterImpl(node);
  }

  public PsiParameterStub createStub(final PsiParameter psi, final StubElement parentStub) {
    final TypeInfo type = TypeInfo.create(psi.getTypeNoResolve(), psi.getTypeElement());
    return new PsiParameterStubImpl(parentStub, psi.getName(), type, psi.isVarArgs());
  }

  public void serialize(final PsiParameterStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeName(stub.getName());
    TypeInfo.writeTYPE(dataStream, stub.getType(false));
    dataStream.writeBoolean(stub.isParameterTypeEllipsis());
  }

  public PsiParameterStub deserialize(final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    StringRef name = dataStream.readName();
    TypeInfo type = TypeInfo.readTYPE(dataStream, parentStub);
    boolean isEll = dataStream.readBoolean();
    return new PsiParameterStubImpl(parentStub, name, type, isEll);
  }

  public boolean shouldCreateStub(final ASTNode node) {
    final IElementType type = node.getTreeParent().getElementType();
    return type == JavaElementType.PARAMETER_LIST;
  }

  public void indexStub(final PsiParameterStub stub, final IndexSink sink) {
  }
}
