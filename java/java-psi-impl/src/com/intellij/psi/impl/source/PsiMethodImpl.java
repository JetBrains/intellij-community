// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.ui.Queryable;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.light.LightCompactConstructorParameter;
import com.intellij.psi.impl.light.LightParameterListBuilder;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stub.JavaStubImplUtil;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PsiMethodImpl extends JavaStubPsiElement<PsiMethodStub> implements PsiMethod, Queryable {
  private PsiType myCachedType;
  private volatile String myCachedName;

  public PsiMethodImpl(PsiMethodStub stub) {
    this(stub, JavaStubElementTypes.METHOD);
  }

  protected PsiMethodImpl(PsiMethodStub stub, IStubElementType<?,?> type) {
    super(stub, type);
  }

  protected PsiMethodImpl(PsiMethodStub stub, IElementType type) {
    super(stub, type);
  }

  public PsiMethodImpl(ASTNode node) {
    super(node);
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    dropCached();
  }

  @MustBeInvokedByOverriders
  protected void dropCached() {
    myCachedType = null;
    myCachedName = null;
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
    PsiClass cc = getContainingClass();
    return cc != null ? cc : super.getContext();
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)getNode().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  @Override
  public PsiMethod @NotNull [] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @Override
  public PsiMethod @NotNull [] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  @Override
  public PsiMethod @NotNull [] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @Override
  public @NotNull List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  @Override
  public PsiMethod findDeepestSuperMethod() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethod(this);
  }

  @Override
  public PsiMethod @NotNull [] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  @Override
  public @NotNull String getName() {
    String name = myCachedName;
    if (name == null) {
      PsiMethodStub stub = getGreenStub();
      if (stub != null) {
        name = stub.getName();
      }
      else {
        PsiIdentifier nameIdentifier = getNameIdentifier();
        name = nameIdentifier == null ? null : nameIdentifier.getText();
      }
    }
    myCachedName = name;
    return name != null ? name : "<unnamed>";
  }

  @Override
  public @NotNull HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiIdentifier identifier = getNameIdentifier();
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
    return getStubOrPsiChild(JavaStubElementTypes.TYPE_PARAMETER_LIST, PsiTypeParameterList.class);
  }

  @Override
  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @Override
  public PsiTypeParameter @NotNull [] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  @Override
  public PsiType getReturnType() {
    if (isConstructor()) return null;

    PsiMethodStub stub = getStub();
    if (stub != null) {
      PsiType type = myCachedType;
      if (type == null) {
        type = JavaSharedImplUtil.createTypeFromStub(this, stub.getReturnTypeText());
        myCachedType = type;
      }
      return type;
    }

    myCachedType = null;
    PsiTypeElement typeElement = getReturnTypeElement();
    return typeElement != null ? JavaSharedImplUtil.getType(typeElement, getParameterList()) : null;
  }

  @Override
  public @NotNull PsiModifierList getModifierList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST, PsiModifierList.class);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  public @NotNull PsiParameterList getParameterList() {
    PsiParameterList list = getStubOrPsiChild(JavaStubElementTypes.PARAMETER_LIST, PsiParameterList.class);
    if (list == null) {
      return CachedValuesManager.getCachedValue(this, () -> {
        LightParameterListBuilder lightList = new LightParameterListBuilder(getManager(), getLanguage()) {
          @Override
          public String getText() {
            return null;
          }
        };
        PsiClass aClass = getContainingClass();
        if (aClass != null) {
          PsiRecordComponent[] recordComponents = aClass.getRecordComponents();
          for (PsiRecordComponent component : recordComponents) {
            String name = component.getName();
            lightList.addParameter(new LightCompactConstructorParameter(name, component.getType(), this, component));
          }
        }

        return CachedValueProvider.Result.create(lightList, this, PsiModificationTracker.MODIFICATION_COUNT);
      });
    }
    return list;
  }

  @Override
  public @NotNull PsiReferenceList getThrowsList() {
    PsiReferenceList child = getStubOrPsiChild(JavaStubElementTypes.THROWS_LIST, PsiReferenceList.class);
    if (child != null) return child;

    PsiMethodStub stub = getStub();
    Stream<String> children =
      stub != null ? stub.getChildrenStubs().stream().map(s -> s.getClass().getSimpleName() + " : " + s.getElementType())
                   : Stream.of(getChildren()).map(e -> e.getClass().getSimpleName() + " : " + e.getNode().getElementType());
    throw new AssertionError("Missing throws list, file=" + getContainingFile() + " children:\n" + children.collect(Collectors.joining("\n")));
  }

  @Override
  public PsiCodeBlock getBody() {
    return (PsiCodeBlock)getNode().findChildByRoleAsPsiElement(ChildRole.METHOD_BODY);
  }

  @Override
  public @NotNull CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  @Override
  public boolean isDeprecated() {
    return JavaStubImplUtil.isMemberDeprecated(this, getGreenStub());
  }

  @Override
  public PsiDocComment getDocComment() {
    PsiMethodStub stub = getGreenStub();
    if (stub != null && !stub.hasDocComment()) return null;

    return (PsiDocComment)getNode().findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
  }

  @Override
  public boolean isConstructor() {
    PsiMethodStub stub = getGreenStub();
    if (stub != null) {
      return stub.isConstructor();
    }

    return getNode().findChildByRole(ChildRole.TYPE) == null;
  }

  @Override
  public boolean isVarArgs() {
    PsiMethodStub stub = getGreenStub();
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
  public @NotNull MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    if (substitutor == PsiSubstitutor.EMPTY) {
      return CachedValuesManager.getCachedValue(this, () -> {
        MethodSignature signature = MethodSignatureBackedByPsiMethod.create(this, PsiSubstitutor.EMPTY);
        return CachedValueProvider.Result.create(signature, PsiModificationTracker.MODIFICATION_COUNT);
      });
    }
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  @Override
  public PsiElement getOriginalElement() {
    PsiClass containingClass = getContainingClass();
    if (containingClass != null) {
      PsiElement original = containingClass.getOriginalElement();
      if (original != containingClass) {
        PsiMethod originalMethod = ((PsiClass)original).findMethodBySignature(this, false);
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
  public Icon getElementIcon(int flags) {
    IconManager iconManager = IconManager.getInstance();
    RowIcon baseIcon = iconManager.createLayeredIcon(this, getBaseIcon(), ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  protected @NotNull Icon getBaseIcon() {
    PlatformIcons iconId = hasModifierProperty(PsiModifier.ABSTRACT) ? PlatformIcons.AbstractMethod : PlatformIcons.Method;
    return IconManager.getInstance().getPlatformIcon(iconId);
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    info.put("methodName", getName());
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }
}