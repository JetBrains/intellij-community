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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PsiReferenceListImpl extends JavaStubPsiElement<PsiClassReferenceListStub> implements PsiReferenceList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiReferenceListImpl");
  private static final TokenSet REFERENCE_BIT_SET = TokenSet.create(Constants.JAVA_CODE_REFERENCE);

  public PsiReferenceListImpl(final PsiClassReferenceListStub stub, final IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public PsiReferenceListImpl(final ASTNode node) {
    super(node);
  }

  @NotNull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    final PsiJavaCodeReferenceElement[] owns =
      calcTreeElement().getChildrenAsPsiElements(REFERENCE_BIT_SET, Constants.PSI_REFERENCE_ELEMENT_ARRAY_CONSTRUCTOR);
    final List<PsiJavaCodeReferenceElement> augments = PsiAugmentProvider.collectAugments(this, PsiJavaCodeReferenceElement.class);
    return ArrayUtil.mergeArrayAndCollection(owns, augments, PsiJavaCodeReferenceElement.ARRAY_FACTORY);
  }

  @NotNull
  public PsiClassType[] getReferencedTypes() {
    final PsiClassReferenceListStub stub = getStub();
    if (stub != null) {
      return stub.getReferencedTypes();
    }

    final PsiJavaCodeReferenceElement[] refs = getReferenceElements();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    PsiClassType[] types = new PsiClassType[refs.length];
    for (int i = 0; i < types.length; i++) {
      types[i] = factory.createType(refs[i]);
    }

    return types;
  }

  public Role getRole() {
    final IElementType tt = getElementType();

    if (tt == JavaElementType.EXTENDS_LIST) {
      return Role.EXTENDS_LIST;
    }
    else if (tt == JavaElementType.IMPLEMENTS_LIST) {
      return Role.IMPLEMENTS_LIST;
    }
    else if (tt == JavaElementType.THROWS_LIST) {
      return Role.THROWS_LIST;
    }
    else if (tt == JavaElementType.EXTENDS_BOUND_LIST) {
      return Role.EXTENDS_BOUNDS_LIST;
    }
    else {
      LOG.error("Unknown element type:" + tt);
      return null;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiReferenceList";
  }
}
