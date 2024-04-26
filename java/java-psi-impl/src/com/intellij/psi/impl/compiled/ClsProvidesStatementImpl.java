// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiProvidesStatementStub;
import com.intellij.psi.impl.source.PsiClassReferenceType;
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
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitProvidesStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiJavaCodeReferenceElement getInterfaceReference() {
    return myClassReference;
  }

  @Override
  public PsiClassType getInterfaceType() {
    return new PsiClassReferenceType(myClassReference, null, PsiAnnotation.EMPTY_ARRAY);
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
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.PROVIDES_STATEMENT);
    setMirror(getInterfaceReference(), SourceTreeToPsiMap.<PsiProvidesStatement>treeToPsiNotNull(element).getInterfaceReference());
    setMirrorIfPresent(getImplementationList(), SourceTreeToPsiMap.<PsiProvidesStatement>treeToPsiNotNull(element).getImplementationList());
  }

  @Override
  public String toString() {
    return "PsiProvidesStatement";
  }
}
