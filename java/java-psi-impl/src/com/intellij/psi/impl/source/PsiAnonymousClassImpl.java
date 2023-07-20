// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.SoftReference;

import static com.intellij.reference.SoftReference.dereference;

public class PsiAnonymousClassImpl extends PsiClassImpl implements PsiAnonymousClass {
  private static final Key<PsiAnonymousClassImpl> STUB_BASE_CLASS_REFERENCE_HOLDER = Key.create("STUB_BASE_CLASS_REFERENCE_HOLDER");
  private SoftReference<PsiClassType> myCachedBaseType;

  public PsiAnonymousClassImpl(PsiClassStub stub) {
    super(stub, JavaStubElementTypes.ANONYMOUS_CLASS);
  }

  public PsiAnonymousClassImpl(ASTNode node) {
    super(node);
  }

  @Override
  protected Object clone() {
    PsiAnonymousClassImpl clone = (PsiAnonymousClassImpl)super.clone();
    clone.myCachedBaseType = null;
    return clone;
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedBaseType = null;
  }

  @Override
  public PsiExpressionList getArgumentList() {
    return (PsiExpressionList)getNode().findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
  }

  @Override
  @NotNull
  public PsiJavaCodeReferenceElement getBaseClassReference() {
    PsiElement baseRef = getFirstChild();
    assert baseRef instanceof PsiJavaCodeReferenceElement : getText();
    return (PsiJavaCodeReferenceElement)baseRef;
  }

  @Override
  @NotNull
  public PsiClassType getBaseClassType() {
    PsiClassStub<?> stub = getGreenStub();
    if (stub == null) {
      myCachedBaseType = null;
      return getTypeByTree();
    }

    PsiClassType type = dereference(myCachedBaseType);
    if (type != null) return type;

    if (!isInQualifiedNew() && !isDiamond(stub)) {
      String refText = stub.getBaseClassReferenceText();
      assert refText != null : stub;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());

      try {
        PsiJavaCodeReferenceElement ref = factory.createReferenceFromText(refText, this);
        ((PsiJavaCodeReferenceElementImpl)ref).setKindWhenDummy(PsiJavaCodeReferenceElementImpl.Kind.CLASS_NAME_KIND);
        ref.getContainingFile().putUserData(STUB_BASE_CLASS_REFERENCE_HOLDER, this);
        type = factory.createType(ref);
      }
      catch (IncorrectOperationException e) {
        type = PsiType.getJavaLangObject(getManager(), getResolveScope());
      }

      myCachedBaseType = new SoftReference<>(type);
      return type;
    }
    else {
      return getTypeByTree();
    }
  }
  
  private boolean isDiamond(@NotNull PsiClassStub<?> stub) {
    if (PsiUtil.isLanguageLevel9OrHigher(this)) {
      String referenceText = stub.getBaseClassReferenceText();
      if (referenceText != null && referenceText.endsWith(">")) {
        return StringUtil.trimEnd(referenceText, ">").trim().endsWith("<");
      }
    }
    return false;
  }

  private PsiClassType getTypeByTree() {
    return JavaPsiFacade.getElementFactory(getProject()).createType(getBaseClassReference());
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public String getQualifiedName() {
    return null;
  }

  @Override
  public PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return name.equals(PsiModifier.FINAL);
  }

  @Override
  public PsiReferenceList getExtendsList() {
    return null;
  }

  @Override
  public PsiReferenceList getImplementsList() {
    return null;
  }

  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @Override
  public boolean isInterface() {
    return false;
  }

  @Override
  public boolean isAnnotationType() {
    return false;
  }

  @Override
  public boolean isEnum() {
    return false;
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @Override
  public PsiElement getOriginalElement() {
    return this;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnonymousClass(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiAnonymousClass";
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (lastParent instanceof PsiExpressionList) return true;

    if (lastParent != null && isBaseClassReference(lastParent)) return true;

    return super.processDeclarations(processor, state, lastParent, place);
  }

  public boolean isBaseClassReference(@NotNull PsiElement element) {
    if (element instanceof PsiJavaCodeReferenceElement) {
      // IMPORTANT: do not call getBaseClassReference() for lastParent which is not under our node - loads tree!
      PsiElement parent = element.getParent();
      return parent == this && element == getBaseClassReference() ||
             isBaseClassReferenceHolder(parent);
    }

    return isBaseClassReferenceHolder(element);
  }

  private boolean isBaseClassReferenceHolder(@NotNull PsiElement element) {
    return element instanceof DummyHolder && element.getUserData(STUB_BASE_CLASS_REFERENCE_HOLDER) == this;
  }

  @Override
  public boolean isInQualifiedNew() {
    PsiClassStub<?> stub = getGreenStub();
    if (stub != null) {
      return stub.isAnonymousInQualifiedNew();
    }

    PsiElement parent = getParent();
    return parent instanceof PsiNewExpression && ((PsiNewExpression)parent).getQualifier() != null;
  }

}
