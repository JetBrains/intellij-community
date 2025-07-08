/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiPackageStatementStub;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import org.jetbrains.annotations.NotNull;

public class PsiPackageStatementImpl extends JavaStubPsiElement<PsiPackageStatementStub> implements PsiPackageStatement {
  public PsiPackageStatementImpl(PsiPackageStatementStub stub) {
    super(stub, JavaStubElementTypes.PACKAGE_STATEMENT);
  }

  public PsiPackageStatementImpl(ASTNode node) {
    super(node);
  }

  @Override
  public @NotNull CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  @Override
  public PsiJavaCodeReferenceElement getPackageReference() {
    return (PsiJavaCodeReferenceElement)getNode().findChildByRoleAsPsiElement(ChildRole.PACKAGE_REFERENCE);
  }

  @Override
  public String getPackageName() {
    PsiPackageStatementStub stub = getGreenStub();
    if (stub != null) {
      return stub.getPackageName();
    }
    PsiJavaCodeReferenceElement ref = getPackageReference();
    return ref == null ? null : JavaSourceUtil.getReferenceText(ref);
  }

  @Override
  public PsiModifierList getAnnotationList() {
    return getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST, PsiModifierList.class);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPackageStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiPackageStatement:" + getPackageName();
  }
}
