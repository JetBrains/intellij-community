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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.stubs.IStubElementType;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class PsiTypeParameterExtendsBoundsListImpl extends JavaStubPsiElement<PsiClassReferenceListStub> implements PsiReferenceList {
  public PsiTypeParameterExtendsBoundsListImpl(final PsiClassReferenceListStub stub, final IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public PsiTypeParameterExtendsBoundsListImpl(final ASTNode node) {
    super(node);
  }

  @Override
  @NotNull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    return calcTreeElement().getChildrenAsPsiElements(Constants.JAVA_CODE_REFERENCE_BIT_SET,
                                                      Constants.PSI_REFERENCE_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  @Override
  @NotNull
  public PsiClassType[] getReferencedTypes() {
    final PsiClassReferenceListStub stub = getStub();
    if (stub != null) return stub.getReferencedTypes();

    return createTypes(getReferenceElements());
  }

  @Override
  public Role getRole() {
    return Role.EXTENDS_BOUNDS_LIST;
  }

  private PsiClassType[] createTypes(final PsiJavaCodeReferenceElement[] refs) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    PsiClassType[] types = new PsiClassType[refs.length];
    for (int i = 0; i < refs.length; i++) {
      types[i] = factory.createType(refs[i]);
    }
    return types;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiElement(EXTENDS_BOUND_LIST)";
  }
}
