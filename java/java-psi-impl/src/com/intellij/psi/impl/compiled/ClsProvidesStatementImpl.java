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
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiProvidesStatement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiProvidesStatementStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;

public class ClsProvidesStatementImpl extends ClsRepositoryPsiElement<PsiProvidesStatementStub> implements PsiProvidesStatement {
  private final ClsJavaCodeReferenceElementImpl myClassReference;

  public ClsProvidesStatementImpl(PsiProvidesStatementStub stub) {
    super(stub);
    myClassReference = new ClsJavaCodeReferenceElementImpl(this, stub.getInterface());
  }

  @Override
  public PsiJavaCodeReferenceElement getInterfaceReference() {
    return myClassReference;
  }

  @Override
  public PsiReferenceList getImplementationList() {
    StubElement<PsiReferenceList> stub = getStub().findChildStubByType(JavaStubElementTypes.PROVIDES_WITH_LIST);
    return stub != null ? stub.getPsi() : null;
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    StringUtil.repeatSymbol(buffer, ' ', indentLevel);
    buffer.append("provides ").append(myClassReference.getCanonicalText()).append(' ');
    appendText(getImplementationList(), indentLevel, buffer);
    buffer.append(";\n");
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.PROVIDES_STATEMENT);
    setMirror(getInterfaceReference(), SourceTreeToPsiMap.<PsiProvidesStatement>treeToPsiNotNull(element).getInterfaceReference());
    setMirrorIfPresent(getImplementationList(), SourceTreeToPsiMap.<PsiProvidesStatement>treeToPsiNotNull(element).getImplementationList());
  }

  @Override
  public String toString() {
    return "PsiProvidesStatement";
  }
}