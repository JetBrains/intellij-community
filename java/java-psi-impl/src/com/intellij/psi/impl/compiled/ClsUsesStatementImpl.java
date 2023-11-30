// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiUsesStatementStub;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

public class ClsUsesStatementImpl extends ClsRepositoryPsiElement<PsiUsesStatementStub> implements PsiUsesStatement {
  private final ClsJavaCodeReferenceElementImpl myClassReference;

  public ClsUsesStatementImpl(PsiUsesStatementStub stub) {
    super(stub);
    myClassReference = new ClsJavaCodeReferenceElementImpl(this, stub.getClassName());
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitUsesStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiJavaCodeReferenceElement getClassReference() {
    return myClassReference;
  }

  @Override
  public PsiClassType getClassType() {
    return new PsiClassReferenceType(myClassReference, null, PsiAnnotation.EMPTY_ARRAY);
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    StringUtil.repeatSymbol(buffer, ' ', indentLevel);
    buffer.append("uses ").append(myClassReference.getCanonicalText()).append(";\n");
  }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.USES_STATEMENT);
    setMirror(getClassReference(), SourceTreeToPsiMap.<PsiUsesStatement>treeToPsiNotNull(element).getClassReference());
  }

  @Override
  public String toString() {
    return "PsiUsesStatement";
  }
}
