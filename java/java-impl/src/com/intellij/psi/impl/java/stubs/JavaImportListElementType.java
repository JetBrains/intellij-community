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
import com.intellij.psi.PsiImportList;
import com.intellij.psi.impl.java.stubs.impl.PsiImportListStubImpl;
import com.intellij.psi.impl.source.PsiImportListImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;

import java.io.IOException;

public class JavaImportListElementType extends JavaStubElementType<PsiImportListStub, PsiImportList> {
  public JavaImportListElementType() {
    super("IMPORT_LIST");
  }

  public PsiImportList createPsi(final PsiImportListStub stub) {
    assert !isCompiled(stub);
    return new PsiImportListImpl(stub);
  }

  public PsiImportList createPsi(final ASTNode node) {
    return new PsiImportListImpl(node);
  }

  public PsiImportListStub createStub(final PsiImportList psi, final StubElement parentStub) {
    return new PsiImportListStubImpl(parentStub);
  }

  public void serialize(final PsiImportListStub stub, final StubOutputStream dataStream)
      throws IOException {
  }

  public PsiImportListStub deserialize(final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    return new PsiImportListStubImpl(parentStub);
  }

  public void indexStub(final PsiImportListStub stub, final IndexSink sink) {
  }
}
