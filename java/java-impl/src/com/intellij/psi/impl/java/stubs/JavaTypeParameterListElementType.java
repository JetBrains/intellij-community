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
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.compiled.ClsTypeParametersListImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiTypeParameterListStubImpl;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterListImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;

import java.io.IOException;

public class JavaTypeParameterListElementType extends JavaStubElementType<PsiTypeParameterListStub, PsiTypeParameterList> {
  public JavaTypeParameterListElementType() {
    super("TYPE_PARAMETER_LIST", true);
  }

  public PsiTypeParameterList createPsi(final PsiTypeParameterListStub stub) {
    if (isCompiled(stub)) {
      return new ClsTypeParametersListImpl(stub);
    }
    else {
      return new PsiTypeParameterListImpl(stub);
    }
  }

  public PsiTypeParameterList createPsi(final ASTNode node) {
    return new PsiTypeParameterListImpl(node);
  }

  public PsiTypeParameterListStub createStub(final PsiTypeParameterList psi, final StubElement parentStub) {
    return new PsiTypeParameterListStubImpl(parentStub);
  }

  public void serialize(final PsiTypeParameterListStub stub, final StubOutputStream dataStream)
      throws IOException {
  }

  public PsiTypeParameterListStub deserialize(final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    return new PsiTypeParameterListStubImpl(parentStub);
  }

  public void indexStub(final PsiTypeParameterListStub stub, final IndexSink sink) {
  }
}