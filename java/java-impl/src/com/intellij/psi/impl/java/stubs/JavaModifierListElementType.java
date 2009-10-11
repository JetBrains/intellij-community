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
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.compiled.ClsModifierListImpl;
import com.intellij.psi.impl.java.stubs.impl.PsiModifierListStubImpl;
import com.intellij.psi.impl.source.PsiModifierListImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.PersistentStringEnumerator;

import java.io.IOException;

public class JavaModifierListElementType extends JavaStubElementType<PsiModifierListStub, PsiModifierList> {
  public JavaModifierListElementType() {
    super("MODIFIER_LIST");
  }

  public PsiModifierList createPsi(final PsiModifierListStub stub) {
    if (isCompiled(stub)) {
      return new ClsModifierListImpl(stub);
    }
    else {
      return new PsiModifierListImpl(stub);
    }
  }

  public PsiModifierList createPsi(final ASTNode node) {
    return new PsiModifierListImpl(node);
  }

  public PsiModifierListStub createStub(final PsiModifierList psi, final StubElement parentStub) {
    return new PsiModifierListStubImpl(parentStub, RecordUtil.packModifierList(psi));
  }

  public void serialize(final PsiModifierListStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeVarInt(stub.getModifiersMask());
  }

  public PsiModifierListStub deserialize(final StubInputStream dataStream, final StubElement parentStub)
      throws IOException {
    return new PsiModifierListStubImpl(parentStub, dataStream.readVarInt());
  }

  public void indexStub(final PsiModifierListStub stub, final IndexSink sink) {
  }
}