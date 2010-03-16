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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.ui.RowIcon;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
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

  public String toString() {
    return "PsiEnumConstant:" + getName();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitEnumConstant(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public PsiExpressionList getArgumentList() {
    return (PsiExpressionList)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
  }

  public PsiEnumConstantInitializer getInitializingClass() {
    return (PsiEnumConstantInitializer)getStubOrPsiChild(JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER);
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : null;
  }

  @Override
  public PsiElement getContext() {
    final PsiClass cc = getContainingClass();
    return cc != null ? cc : super.getContext();
  }

  public PsiModifierList getModifierList() {
    return getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return PsiModifier.PUBLIC.equals(name) || PsiModifier.STATIC.equals(name) || PsiModifier.FINAL.equals(name);
  }

  @NotNull
  public PsiType getType() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(getContainingClass());
  }

  public PsiTypeElement getTypeElement() {
    return null;
  }

  public PsiExpression getInitializer() {
    return null;
  }

  public boolean hasInitializer() {
    return true;
  }

  public void normalizeDeclaration() throws IncorrectOperationException { }

  public Object computeConstantValue() {
    return this;
  }

  public PsiMethod resolveMethod() {
    PsiClass containingClass = getContainingClass();
    LOG.assertTrue(containingClass != null);
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    JavaResolveResult resolveResult = facade.getResolveHelper()
      .resolveConstructor(facade.getElementFactory().createType(containingClass), getArgumentList(), this);
    return (PsiMethod)resolveResult.getElement();
  }

  @NotNull
  public JavaResolveResult resolveMethodGenerics() {
    PsiClass containingClass = getContainingClass();
    LOG.assertTrue(containingClass != null);
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    return facade.getResolveHelper().resolveConstructor(facade.getElementFactory().createType(containingClass), getArgumentList(), this);
  }

  @NotNull
  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  @NotNull
  public String getName() {
    final PsiFieldStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return getNameIdentifier().getText();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  public PsiDocComment getDocComment() {
    return (PsiDocComment)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
  }

  public boolean isDeprecated() {
    final PsiFieldStub stub = getStub();
    if (stub != null) {
      return stub.isDeprecated();
    }

    PsiDocComment docComment = getDocComment();
    return docComment != null && docComment.findTagByName("deprecated") != null ||
           getModifierList().findAnnotation("java.lang.Deprecated") != null;
  }

  public PsiReference getReference() {
    return myReference;
  }

  public PsiMethod resolveConstructor() {
    return resolveMethod();
  }

  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon = createLayeredIcon(Icons.FIELD_ICON, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  private class MyReference implements PsiJavaReference {
    public PsiElement getElement() {
      return PsiEnumConstantImpl.this;
    }

    public TextRange getRangeInElement() {
      PsiIdentifier nameIdentifier = getNameIdentifier();
      int startOffsetInParent = nameIdentifier.getStartOffsetInParent();
      return new TextRange(startOffsetInParent, startOffsetInParent + nameIdentifier.getTextLength());
    }

    public boolean isSoft() {
      return false;
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return getElement();
    }

    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      throw new IncorrectOperationException("Invalid operation");
    }

    @NotNull
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    public void processVariants(PsiScopeProcessor processor) {
    }

    @NotNull
    public JavaResolveResult[] multiResolve(boolean incompleteCode) {
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      PsiClassType type = facade.getElementFactory().createType(getContainingClass());
      return facade.getResolveHelper().multiResolveConstructor(type, getArgumentList(), getElement());
    }

    @NotNull
    public JavaResolveResult advancedResolve(boolean incompleteCode) {
      final JavaResolveResult[] results = multiResolve(incompleteCode);
      if (results.length == 1) return results[0];
      return JavaResolveResult.EMPTY;
    }

    public PsiElement resolve() {
      return advancedResolve(false).getElement();
    }

    public String getCanonicalText() {
      return getContainingClass().getName();
    }

    public boolean isReferenceTo(PsiElement element) {
      return element instanceof PsiMethod
             && ((PsiMethod)element).isConstructor()
             && ((PsiMethod)element).getContainingClass() == getContainingClass()
             && getManager().areElementsEquivalent(resolve(), element);
    }
  }

  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getFieldPresentation(this);
  }
  public void setInitializer(PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isFieldEquivalentTo(this, another);
  }
  public PsiType getTypeNoResolve() {
    return getType();
  }
}
