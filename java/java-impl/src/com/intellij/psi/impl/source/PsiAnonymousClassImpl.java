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
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatchedSoftReference;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class PsiAnonymousClassImpl extends PsiClassImpl implements PsiAnonymousClass {
  private PatchedSoftReference<PsiClassType> myCachedBaseType = null;

  public PsiAnonymousClassImpl(final PsiClassStub stub) {
    super(stub, JavaStubElementTypes.ANONYMOUS_CLASS);
  }

  public PsiAnonymousClassImpl(final ASTNode node) {
    super(node);
  }

  protected Object clone() {
    PsiAnonymousClassImpl clone = (PsiAnonymousClassImpl)super.clone();
    clone.myCachedBaseType = null;
    return clone;
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedBaseType = null;
  }

  public PsiExpressionList getArgumentList() {
    return (PsiExpressionList)getNode().findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
  }

  @NotNull
  public PsiJavaCodeReferenceElement getBaseClassReference() {
    final PsiJavaCodeReferenceElement result =
      (PsiJavaCodeReferenceElement)getNode().findChildByRoleAsPsiElement(ChildRole.BASE_CLASS_REFERENCE);
    assert result != null;
    return result;
  }

  @NotNull
  public PsiClassType getBaseClassType() {
    final PsiClassStub stub = getStub();
    if (stub == null) {
      myCachedBaseType = null;
      return getTypeByTree();
    }

    PsiClassType type = null;
    if (myCachedBaseType != null) type = myCachedBaseType.get();
    if (type != null) return type;

    if (!isInQualifiedNew()) {
      final PsiJavaCodeReferenceElement ref;
      final String refText = stub.getBaseClassReferenceText();
      assert refText != null : stub;
      final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();

      final PsiElement context = calcBasesResolveContext(PsiNameHelper.getShortClassName(refText), getExtendsList());
      try {
        ref = factory.createReferenceFromText(refText, context);
        ((PsiJavaCodeReferenceElementImpl)ref).setKindWhenDummy(PsiJavaCodeReferenceElementImpl.CLASS_NAME_KIND);
        type = factory.createType(ref);
      }
      catch (IncorrectOperationException e) {
        type = PsiClassType.getJavaLangObject(getManager(), getResolveScope());
      }

      myCachedBaseType = new PatchedSoftReference<PsiClassType>(type);
      return type;
    }
    else {
      return getTypeByTree();
    }
  }

  private PsiClassType getTypeByTree() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(getBaseClassReference());
  }

  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  public String getQualifiedName() {
    return null;
  }

  public PsiModifierList getModifierList() {
    return null;
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return name.equals(PsiModifier.FINAL);
  }

  public PsiReferenceList getExtendsList() {
    return null;
  }

  public PsiReferenceList getImplementsList() {
    return null;
  }

  public PsiClass getContainingClass() {
    return null;
  }

  public boolean isInterface() {
    return false;
  }

  public boolean isAnnotationType() {
    return false;
  }

  public boolean isEnum() {
    return false;
  }

  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  public PsiElement getOriginalElement() {
    return this;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnonymousClass(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiAnonymousClass";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (lastParent instanceof PsiExpressionList) return true;
    if (lastParent != null/* IMPORTANT: do not call getBaseClassReference() for lastParent == null and lastParent which is not under our node - loads tree!*/
        && lastParent.getParent() == this && lastParent == getBaseClassReference()) {
      return true;
    }
    return super.processDeclarations(processor, state, lastParent, place);
  }

  public boolean isInQualifiedNew() {
    final PsiClassStub stub = getStub();
    if (stub != null) {
      return stub.isAnonymousInQualifiedNew();
    }

    final PsiElement parent = getParent();
    return parent instanceof PsiNewExpression && ((PsiNewExpression)parent).getQualifier() != null;
  }

  public PsiElement getParent() {
    return SharedImplUtil.getParent(getNode());
  }
}
