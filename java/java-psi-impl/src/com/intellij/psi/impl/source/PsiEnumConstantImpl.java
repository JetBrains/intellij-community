// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stub.JavaStubImplUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author dsl
 */
public class PsiEnumConstantImpl extends JavaStubPsiElement<PsiFieldStub> implements PsiEnumConstant {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiEnumConstantImpl");
  private final MyReference myReference = new MyReference();

  public PsiEnumConstantImpl(final PsiFieldStub stub) {
    super(stub, JavaStubElementTypes.ENUM_CONSTANT);
  }

  public PsiEnumConstantImpl(final ASTNode node) {
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
    return (PsiEnumConstantInitializer)getStubOrPsiChild(JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER);
  }

  @NotNull
  @Override
  public PsiEnumConstantInitializer getOrCreateInitializingClass() {
    final PsiEnumConstantInitializer initializingClass = getInitializingClass();
    if (initializingClass != null) return initializingClass;

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    final PsiEnumConstantInitializer initializer = factory.createEnumConstantFromText("foo{}", null).getInitializingClass();
    LOG.assertTrue(initializer != null);

    final PsiExpressionList argumentList = getArgumentList();
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
    final PsiClass cc = getContainingClass();
    return cc != null ? cc : super.getContext();
  }

  @Override
  public PsiModifierList getModifierList() {
    return getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return PsiModifier.PUBLIC.equals(name) || PsiModifier.STATIC.equals(name) || PsiModifier.FINAL.equals(name);
  }

  @Override
  @NotNull
  public PsiType getType() {
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
  @NotNull
  public JavaResolveResult resolveMethodGenerics() {
    return CachedValuesManager.getCachedValue(this, () -> {
      PsiClass containingClass = getContainingClass();
      LOG.assertTrue(containingClass != null);
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      return new CachedValueProvider.Result<>(facade.getResolveHelper().resolveConstructor(facade.getElementFactory().createType(containingClass), getArgumentList(), this),
                                              PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  @Override
  @NotNull
  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  @Override
  @NotNull
  public String getName() {
    final PsiFieldStub stub = getGreenStub();
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
  public PsiMethod resolveConstructor() {
    return resolveMethod();
  }

  @Override
  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon =
      IconManager.getInstance().createLayeredIcon(this, PlatformIcons.FIELD_ICON, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  private class MyReference implements PsiJavaReference {
    @NotNull
    @Override
    public PsiElement getElement() {
      return PsiEnumConstantImpl.this;
    }

    @NotNull
    @Override
    public TextRange getRangeInElement() {
      PsiIdentifier nameIdentifier = getNameIdentifier();
      int startOffsetInParent = nameIdentifier.getStartOffsetInParent();
      return new TextRange(startOffsetInParent, startOffsetInParent + nameIdentifier.getTextLength());
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
    @NotNull
    public JavaResolveResult[] multiResolve(boolean incompleteCode) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      PsiClassType type = facade.getElementFactory().createType(getContainingClass());
      return facade.getResolveHelper().multiResolveConstructor(type, getArgumentList(), getElement());
    }

    @Override
    @NotNull
    public JavaResolveResult advancedResolve(boolean incompleteCode) {
      final JavaResolveResult[] results = multiResolve(incompleteCode);
      if (results.length == 1) return results[0];
      return JavaResolveResult.EMPTY;
    }

    @Override
    public PsiElement resolve() {
      return advancedResolve(false).getElement();
    }

    @Override
    @NotNull
    public String getCanonicalText() {
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
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isFieldEquivalentTo(this, another);
  }
}
