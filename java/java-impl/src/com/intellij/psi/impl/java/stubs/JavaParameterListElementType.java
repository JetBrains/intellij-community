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
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.compiled.ClsParameterListImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiParameterListStubImpl;
import com.intellij.psi.impl.source.PsiParameterListImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.IOException;

public class JavaParameterListElementType extends JavaStubElementType<PsiParameterListStub, PsiParameterList> {
  public JavaParameterListElementType() {
    super("PARAMETER_LIST");
  }

  public PsiParameterList createPsi(final PsiParameterListStub stub) {
    if (isCompiled(stub)) {
      return new ClsParameterListImpl(stub);
    }
    else {
      return new PsiParameterListImpl(stub);
    }
  }

  public PsiParameterList createPsi(final ASTNode node) {
    return new PsiParameterListImpl(node);
  }

  public PsiParameterListStub createStub(final PsiParameterList psi, final StubElement parentStub) {
    return new PsiParameterListStubImpl(parentStub);
  }

  public void serialize(final PsiParameterListStub stub, final StubOutputStream dataStream)
      throws IOException {
  }

  public PsiParameterListStub deserialize(final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    return new PsiParameterListStubImpl(parentStub);
  }

  public void indexStub(final PsiParameterListStub stub, final IndexSink sink) {
  }
}