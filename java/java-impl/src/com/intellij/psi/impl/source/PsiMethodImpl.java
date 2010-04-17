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
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Queryable;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.RowIcon;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatchedSoftReference;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public class PsiMethodImpl extends JavaStubPsiElement<PsiMethodStub> implements PsiMethod, Queryable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiMethodImpl");

  private PatchedSoftReference<PsiType> myCachedType = null;

  public PsiMethodImpl(final PsiMethodStub stub) {
    this(stub, JavaStubElementTypes.METHOD);
  }

  protected PsiMethodImpl(final PsiMethodStub stub, final IStubElementType type) {
    super(stub, type);
  }

  public PsiMethodImpl(final ASTNode node) {
    super(node);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    dropCached();
  }

  protected void dropCached() {
    myCachedType = null;
  }

  protected Object clone() {
    PsiMethodImpl clone = (PsiMethodImpl)super.clone();
    clone.dropCached();
    return clone;
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : PsiTreeUtil.getParentOfType(this, JspClass.class);
  }

  @Override
  public PsiElement getContext() {
    final PsiClass cc = getContainingClass();
    return cc != null ? cc : super.getContext();
  }

  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)getNode().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  public PsiMethod findDeepestSuperMethod() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethod(this);
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  @NotNull
  public String getName() {
    final String name;
    final PsiMethodStub stub = getStub();
    if (stub != null) {
      name = stub.getName();
    }
    else {
      final PsiIdentifier nameIdentifier = getNameIdentifier();
      name = nameIdentifier == null ? null : nameIdentifier.getText();
    }

    return name != null ? name : "<unnamed>";
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  public PsiMethodReceiver getMethodReceiver() {
    ASTNode node = getNode().findChildByType(JavaElementType.METHOD_RECEIVER);
    if (node == null) return null;
    return (PsiMethodReceiver)node.getPsi();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException{
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  public PsiTypeElement getReturnTypeElement() {
    if (isConstructor()) return null;
    return (PsiTypeElement)getNode().findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  public PsiTypeParameterList getTypeParameterList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.TYPE_PARAMETER_LIST);
  }

  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @NotNull public PsiTypeParameter[] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  public PsiType getReturnTypeNoResolve() {
    if (isConstructor()) return null;

    final PsiMethodStub stub = getStub();
    if (stub != null) {
      final String typeText = TypeInfo.createTypeText(stub.getReturnTypeText(false));
      if (typeText == null) return null;

      try {
        return JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeFromText(typeText, this);
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
        return null;
      }
    }

    PsiTypeElement typeElement = getReturnTypeElement();
    if (typeElement == null) return null;
    PsiParameterList parameterList = getParameterList();
    return JavaSharedImplUtil.getTypeNoResolve(typeElement, parameterList, this);
  }

  public PsiType getReturnType() {
    if (isConstructor()) return null;

    final PsiMethodStub stub = getStub();
    if (stub != null) {
      final String typeText = TypeInfo.createTypeText(stub.getReturnTypeText(true));
      if (typeText == null) return null;

      PatchedSoftReference<PsiType> cachedType = myCachedType;
      if (cachedType != null) {
        PsiType type = cachedType.get();
        if (type != null) return type;
      }

      try{
        final PsiType type = JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeFromText(typeText, this);
        myCachedType = new PatchedSoftReference<PsiType>(type);
        return type;
      }
      catch(IncorrectOperationException e){
        LOG.error(e);
        return null;
      }
    }

    myCachedType = null;
    PsiTypeElement typeElement = getReturnTypeElement();
    if (typeElement == null) return null;
    PsiParameterList parameterList = getParameterList();
    return JavaSharedImplUtil.getType(typeElement, parameterList, this);
  }

  @NotNull
  public PsiModifierList getModifierList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @NotNull
  public PsiParameterList getParameterList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.PARAMETER_LIST);
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.THROWS_LIST);
  }

  public PsiCodeBlock getBody() {
    return (PsiCodeBlock)getNode().findChildByRoleAsPsiElement(ChildRole.METHOD_BODY);
  }

  @NotNull
  public CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  public boolean isDeprecated() {
    final PsiMethodStub stub = getStub();
    if (stub != null) {
      return stub.isDeprecated() || stub.hasDeprecatedAnnotation() && PsiImplUtil.isDeprecatedByAnnotation(this);
    }

    return PsiImplUtil.isDeprecatedByDocTag(this) || PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  public PsiDocComment getDocComment() {
    return (PsiDocComment)getNode().findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
  }

  public boolean isConstructor() {
    final PsiMethodStub stub = getStub();
    if (stub != null) {
      return stub.isConstructor();
    }

    return getNode().findChildByRole(ChildRole.TYPE) == null;
  }

  public boolean isVarArgs() {
    final PsiMethodStub stub = getStub();
    if (stub != null) {
      return stub.isVarArgs();
    }

    PsiParameter[] parameters = getParameterList().getParameters();
    return parameters.length > 0 && parameters[parameters.length - 1].isVarArgs();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMethod(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiMethod:" + getName();
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return PsiImplUtil.processDeclarationsInMethod(this, processor, state, lastParent, place);

  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor){
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  public PsiElement getOriginalElement() {
    PsiClass originalClass = (PsiClass)getContainingClass().getOriginalElement();
    final PsiMethod originalMethod = originalClass.findMethodBySignature(this, false);
    return originalMethod != null ? originalMethod : this;
  }

  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getMethodPresentation(this);
  }

  public Icon getElementIcon(final int flags) {
    Icon methodIcon = hasModifierProperty(PsiModifier.ABSTRACT) ? Icons.ABSTRACT_METHOD_ICON : Icons.METHOD_ICON;
    RowIcon baseIcon = createLayeredIcon(methodIcon, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }

  @NotNull
  public SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }

  public void putInfo(Map<String, String> info) {
    info.put("methodName", getName());
  }
}
