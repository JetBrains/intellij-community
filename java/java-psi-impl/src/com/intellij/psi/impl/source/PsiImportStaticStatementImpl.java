/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiImportStatementStub;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiImportStaticStatementImpl extends PsiImportStatementBaseImpl implements PsiImportStaticStatement {
  public static final PsiImportStaticStatementImpl[] EMPTY_ARRAY = new PsiImportStaticStatementImpl[0];
  public static final ArrayFactory<PsiImportStaticStatementImpl> ARRAY_FACTORY = new ArrayFactory<PsiImportStaticStatementImpl>() {
    @NotNull
    @Override
    public PsiImportStaticStatementImpl[] create(final int count) {
      return count == 0 ? EMPTY_ARRAY : new PsiImportStaticStatementImpl[count];
    }
  };

  public PsiImportStaticStatementImpl(final PsiImportStatementStub stub) {
    super(stub, JavaStubElementTypes.IMPORT_STATIC_STATEMENT);
  }

  public PsiImportStaticStatementImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public PsiClass resolveTargetClass() {
    final PsiJavaCodeReferenceElement classReference = getClassReference();
    if (classReference == null) return null;
    final PsiElement result = classReference.resolve();
    if (result instanceof PsiClass) {
      return (PsiClass) result;
    }
    else {
      return null;
    }
  }

  @Override
  public String getReferenceName() {
    if (isOnDemand()) return null;
    final PsiImportStaticReferenceElement memberReference = getMemberReference();
    if (memberReference != null) {
      return memberReference.getReferenceName();
    }
    else {
      return null;
    }
  }

  @Nullable
  private PsiImportStaticReferenceElement getMemberReference() {
    if (isOnDemand()) {
      return null;
    }
    else {
      return (PsiImportStaticReferenceElement) getImportReference();
    }
  }

  @Nullable
  public PsiJavaCodeReferenceElement getClassReference() {
    if (isOnDemand()) {
      return getImportReference();
    }
    else {
      final PsiImportStaticReferenceElement memberReference = getMemberReference();
      if (memberReference != null) {
        return memberReference.getClassReference();
      }
      else {
        return null;
      }
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitImportStaticStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiImportStaticStatement";
  }
}