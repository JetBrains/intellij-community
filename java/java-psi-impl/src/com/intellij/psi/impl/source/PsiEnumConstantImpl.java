// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolService;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stub.JavaStubImplUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

public class PsiEnumConstantImpl extends JavaStubPsiElement<PsiFieldStub> implements PsiEnumConstant {
  private static final Logger LOG = Logger.getInstance(PsiEnumConstantImpl.class);
  private final MyReference myReference = new MyReference();

  public PsiEnumConstantImpl(PsiFieldStub stub) {
    super(stub, JavaStubElementTypes.ENUM_CONSTANT);
  }

  public PsiEnumConstantImpl(ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "PsiEnumConstant:" + getName();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitEnumConstant(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiExpressionList getArgumentList() {
    return (PsiExpressionList)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
  }

  @Override
  public PsiEnumConstantInitializer getInitializingClass() {
    return getStubOrPsiChild(JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER, PsiEnumConstantInitializer.class);
  }

  @Override
  public @NotNull PsiEnumConstantInitializer getOrCreateInitializingClass() {
    PsiEnumConstantInitializer initializingClass = getInitializingClass();
    if (initializingClass != null) return initializingClass;

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    PsiEnumConstantInitializer initializer = factory.createEnumConstantFromText("foo{}", null).getInitializingClass();
    LOG.assertTrue(initializer != null);

    PsiExpressionList argumentList = getArgumentList();
    if (argumentList != null) {
      return (PsiEnumConstantInitializer)addAfter(initializer, argumentList);
    }
    else {
      return (PsiEnumConstantInitializer)addAfter(initializer, getNameIdentifier());
    }
  }

  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : null;
  }

  @Override
  public PsiElement getContext() {
    PsiClass cc = getContainingClass();
    return cc != null ? cc : super.getContext();
  }

  @Override
  public PsiModifierList getModifierList() {
    return getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST, PsiModifierList.class);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return PsiModifier.PUBLIC.equals(name) || PsiModifier.STATIC.equals(name) || PsiModifier.FINAL.equals(name);
  }

  @Override
  public @NotNull PsiType getType() {
    return JavaPsiFacade.getElementFactory(getProject()).createType(getContainingClass());
  }

  @Override
  public PsiTypeElement getTypeElement() {
    return null;
  }

  @Override
  public PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return true;
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException { }

  @Override
  public Object computeConstantValue() {
    return this;
  }

  @Override
  public PsiMethod resolveMethod() {
    return (PsiMethod)resolveMethodGenerics().getElement();
  }

  @Override
  public JavaResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    return myReference.multiResolve(incompleteCode);
  }

  @Override
  public @NotNull JavaResolveResult resolveMethodGenerics() {
    return CachedValuesManager.getCachedValue(this, () -> {
      PsiClass containingClass = getContainingClass();
      LOG.assertTrue(containingClass != null);
      JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      return new CachedValueProvider.Result<>(facade.getResolveHelper().resolveConstructor(facade.getElementFactory().createType(containingClass), getArgumentList(), this),
                                              PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @Override
  public @NotNull PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  @Override
  public @NotNull String getName() {
    PsiFieldStub stub = getGreenStub();
    if (stub != null) {
      return stub.getName();
    }
    return getNameIdentifier().getText();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @Override
  public PsiDocComment getDocComment() {
    return (PsiDocComment)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
  }

  @Override
  public boolean isDeprecated() {
    return JavaStubImplUtil.isMemberDeprecated(this, getGreenStub());
  }

  @Override
  public PsiReference getReference() {
    return myReference;
  }

  @Override
  public @NotNull Collection<? extends @NotNull PsiSymbolReference> getOwnReferences() {
    return Collections.singletonList(PsiSymbolService.getInstance().asSymbolReference(myReference));
  }

  @Override
  public PsiMethod resolveConstructor() {
    return resolveMethod();
  }

  @Override
  public Icon getElementIcon(int flags) {
    IconManager iconManager = IconManager.getInstance();
    RowIcon baseIcon =
      iconManager.createLayeredIcon(this, iconManager.getPlatformIcon(PlatformIcons.Field), ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  private class MyReference implements PsiJavaReference {
    @Override
    public @NotNull PsiElement getElement() {
      return PsiEnumConstantImpl.this;
    }

    @Override
    public @NotNull TextRange getRangeInElement() {
      PsiIdentifier nameIdentifier = getNameIdentifier();
      int startOffsetInParent = nameIdentifier.getStartOffsetInParent();
      if (Registry.is("java.empty.enum.constructor.ref")) {
        return TextRange.from(startOffsetInParent + nameIdentifier.getTextLength(), 0);
      }
      else {
        return TextRange.from(startOffsetInParent, nameIdentifier.getTextLength());
      }
    }

    @Override
    public boolean isSoft() {
      return false;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
      return getElement();
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      throw new IncorrectOperationException("Invalid operation");
    }

    @Override
    public void processVariants(@NotNull PsiScopeProcessor processor) {
    }

    @Override
    public JavaResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      PsiClass containingClass = getContainingClass();
      if (containingClass == null) return JavaResolveResult.EMPTY_ARRAY;
      PsiClassType type = facade.getElementFactory().createType(containingClass);
      return facade.getResolveHelper().multiResolveConstructor(type, getArgumentList(), getElement());
    }

    @Override
    public @NotNull JavaResolveResult advancedResolve(boolean incompleteCode) {
      JavaResolveResult[] results = multiResolve(incompleteCode);
      if (results.length == 1) return results[0];
      return JavaResolveResult.EMPTY;
    }

    @Override
    public PsiElement resolve() {
      return advancedResolve(false).getElement();
    }

    @Override
    public @NotNull String getCanonicalText() {
      return getContainingClass().getName();
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
      return element instanceof PsiMethod
             && ((PsiMethod)element).isConstructor()
             && ((PsiMethod)element).getContainingClass() == getContainingClass()
             && getManager().areElementsEquivalent(resolve(), element);
    }
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public void setInitializer(PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return PsiClassImplUtil.isFieldEquivalentTo(this, another);
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }
}
