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
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.impl.java.stubs.PsiImportStatementStub;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiUtilCore;

/**
 * @author dsl
 */
public abstract class PsiImportStatementBaseImpl extends JavaStubPsiElement<PsiImportStatementStub> implements PsiImportStatementBase{
  public static final PsiImportStatementBaseImpl[] EMPTY_ARRAY = new PsiImportStatementBaseImpl[0];

  protected PsiImportStatementBaseImpl(final PsiImportStatementStub stub, final IStubElementType type) {
    super(stub, type);
  }

  protected PsiImportStatementBaseImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public boolean isOnDemand(){
    final PsiImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.isOnDemand();
    }

    return calcTreeElement().findChildByRoleAsPsiElement(ChildRole.IMPORT_ON_DEMAND_DOT) != null;
  }

  @Override
  public PsiJavaCodeReferenceElement getImportReference() {
    PsiUtilCore.ensureValid(this);
    final PsiImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.getReference();
    }
    return (PsiJavaCodeReferenceElement)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.IMPORT_REFERENCE);
  }

  @Override
  public PsiElement resolve() {
    final PsiJavaCodeReferenceElement reference = getImportReference();
    return reference == null ? null : reference.resolve();
  }
}