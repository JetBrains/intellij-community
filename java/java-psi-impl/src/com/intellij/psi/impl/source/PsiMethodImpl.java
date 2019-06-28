// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.ui.Queryable;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stub.JavaStubImplUtil;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.*;
import com.intellij.reference.SoftReference;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PsiMethodImpl extends JavaStubPsiElement<PsiMethodStub> implements PsiMethod, Queryable {
  private SoftReference<PsiType> myCachedType;

  public PsiMethodImpl(final PsiMethodStub stub) {
    this(stub, JavaStubElementTypes.METHOD);
  }

  protected PsiMethodImpl(final PsiMethodStub stub, final IStubElementType type) {
    super(stub, type);
  }

  public PsiMethodImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    dropCached();
  }

  protected void dropCached() {
    myCachedType = null;
  }

  @Override
  protected Object clone() {
    PsiMethodImpl clone = (PsiMethodImpl)super.clone();
    clone.dropCached();
    return clone;
  }

  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : PsiTreeUtil.getParentOfType(this, PsiSyntheticClass.class);
  }

  @Override
  public PsiElement getContext() {
    final PsiClass cc = getContainingClass();
    return cc != null ? cc : super.getContext();
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)getNode().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  @Override
  @NotNull
  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @Override
  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  @Override
  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @Override
  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  @Override
  public PsiMethod findDeepestSuperMethod() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethod(this);
  }

  @Override
  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  @Override
  @NotNull
  public String getName() {
    final String name;
    final PsiMethodStub stub = getGreenStub();
    if (stub != null) {
      name = stub.getName();
    }
    else {
      final PsiIdentifier nameIdentifier = getNameIdentifier();
      name = nameIdentifier == null ? null : nameIdentifier.getText();
    }

    return name != null ? name : "<unnamed>";
  }

  @Override
  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final PsiIdentifier identifier = getNameIdentifier();
    if (identifier == null) throw new IncorrectOperationException("Empty name: " + this);
    PsiImplUtil.setName(identifier, name);
    return this;
  }

  @Override
  public PsiTypeElement getReturnTypeElement() {
    if (isConstructor()) return null;
    return (PsiTypeElement)getNode().findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.TYPE_PARAMETER_LIST);
  }

  @Override
  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @Override
  @NotNull public PsiTypeParameter[] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  @Override
  public PsiType getReturnType() {
    if (isConstructor()) return null;

    PsiMethodStub stub = getStub();
    if (stub != null) {
      PsiType type = SoftReference.dereference(myCachedType);
      if (type == null) {
        String typeText = TypeInfo.createTypeText(stub.getReturnTypeText(false));
        assert typeText != null : stub;
        type = JavaPsiFacade.getInstance(getProject()).getParserFacade().createTypeFromText(typeText, this);
        type = JavaSharedImplUtil.applyAnnotations(type, getModifierList());
        myCachedType = new SoftReference<>(type);
      }
      return type;
    }

    myCachedType = null;
    PsiTypeElement typeElement = getReturnTypeElement();
    return typeElement != null ? JavaSharedImplUtil.getType(typeElement, getParameterList()) : null;
  }

  @Override
  @NotNull
  public PsiModifierList getModifierList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  @NotNull
  public PsiParameterList getParameterList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.PARAMETER_LIST);
  }

  @Override
  @NotNull
  public PsiReferenceList getThrowsList() {
    PsiReferenceList child = getStubOrPsiChild(JavaStubElementTypes.THROWS_LIST);
    if (child != null) return child;

    PsiMethodStub stub = getStub();
    Stream<String> children =
      stub != null ? stub.getChildrenStubs().stream().map(s -> s.getClass().getSimpleName() + " : " + s.getStubType())
                   : Stream.of(getChildren()).map(e -> e.getClass().getSimpleName() + " : " + e.getNode().getElementType());
    throw new AssertionError("Missing throws list, file=" + getContainingFile() + " children:\n" + children.collect(Collectors.joining("\n")));
  }

  @Override
  public PsiCodeBlock getBody() {
    return (PsiCodeBlock)getNode().findChildByRoleAsPsiElement(ChildRole.METHOD_BODY);
  }

  @Override
  @NotNull
  public CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  @Override
  public boolean isDeprecated() {
    return JavaStubImplUtil.isMemberDeprecated(this, getGreenStub());
  }

  @Override
  public PsiDocComment getDocComment() {
    final PsiMethodStub stub = getGreenStub();
    if (stub != null && !stub.hasDocComment()) return null;

    return (PsiDocComment)getNode().findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
  }

  @Override
  public boolean isConstructor() {
    final PsiMethodStub stub = getGreenStub();
    if (stub != null) {
      return stub.isConstructor();
    }

    return getNode().findChildByRole(ChildRole.TYPE) == null;
  }

  @Override
  public boolean isVarArgs() {
    final PsiMethodStub stub = getGreenStub();
    if (stub != null) {
      return stub.isVarArgs();
    }

    return PsiImplUtil.isVarArgs(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMethod(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiMethod:" + getName();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return PsiImplUtil.processDeclarationsInMethod(this, processor, state, lastParent, place);

  }

  @Override
  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    if (substitutor == PsiSubstitutor.EMPTY) {
      return CachedValuesManager.getCachedValue(this, () -> {
        MethodSignature signature = MethodSignatureBackedByPsiMethod.create(this, PsiSubstitutor.EMPTY);
        return CachedValueProvider.Result.create(signature, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
      });
    }
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  @Override
  public PsiElement getOriginalElement() {
    final PsiClass containingClass = getContainingClass();
    if (containingClass != null) {
      PsiElement original = containingClass.getOriginalElement();
      if (original != containingClass) {
        final PsiMethod originalMethod = ((PsiClass)original).findMethodBySignature(this, false);
        if (originalMethod != null) {
          return originalMethod;
        }
      }
    }
    return this;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public Icon getElementIcon(final int flags) {
    Icon methodIcon = hasModifierProperty(PsiModifier.ABSTRACT) ? PlatformIcons.ABSTRACT_METHOD_ICON : PlatformIcons.METHOD_ICON;
    RowIcon baseIcon = IconManager.getInstance().createLayeredIcon(this, methodIcon, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    return ReadAction.compute(() -> PsiImplUtil.getMemberUseScope(this));
  }

  @Override
  public void putInfo(@NotNull Map<String, String> info) {
    info.put("methodName", getName());
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }
}