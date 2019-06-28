// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author ven
 */
public class LightMethod extends LightElement implements PsiMethod {

  protected final @NotNull PsiMethod myMethod;
  protected final @NotNull PsiClass myContainingClass;
  protected final @NotNull PsiSubstitutor mySubstitutor;

  public LightMethod(@NotNull PsiClass containingClass, @NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor) {
    this(containingClass.getManager(), method, containingClass, containingClass.getLanguage(), substitutor);
  }

  public LightMethod(@NotNull PsiManager manager, @NotNull PsiMethod method, @NotNull PsiClass containingClass) {
    this(manager, method, containingClass, PsiSubstitutor.EMPTY);
  }

  public LightMethod(@NotNull PsiManager manager,
                     @NotNull PsiMethod method,
                     @NotNull PsiClass containingClass,
                     @NotNull PsiSubstitutor substitutor) {
    this(manager, method, containingClass, JavaLanguage.INSTANCE, substitutor);
  }

  public LightMethod(@NotNull PsiManager manager,
                     @NotNull PsiMethod method,
                     @NotNull PsiClass containingClass,
                     @NotNull Language language) {
    this(manager, method, containingClass, language, PsiSubstitutor.EMPTY);
  }

  public LightMethod(@NotNull PsiManager manager,
                     @NotNull PsiMethod method,
                     @NotNull PsiClass containingClass,
                     @NotNull Language language,
                     @NotNull PsiSubstitutor substitutor) {
    super(manager, language);
    myMethod = method;
    myContainingClass = containingClass;
    mySubstitutor = substitutor;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public boolean hasTypeParameters() {
    return myMethod.hasTypeParameters();
  }

  @Override
  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return myMethod.getTypeParameters();
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return myMethod.getTypeParameterList();
  }

  @Override
  public PsiDocComment getDocComment() {
    return myMethod.getDocComment();
  }

  @Override
  public boolean isDeprecated() {
    return myMethod.isDeprecated();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    return myMethod.setName(name);
  }

  @Override
  @NotNull
  public String getName() {
    return myMethod.getName();
  }

  @Override
  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return myMethod.getHierarchicalMethodSignature();
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return myMethod.hasModifierProperty(name);
  }

  @Override
  public TextRange getTextRange() {
    return myMethod.getTextRange();
  }

  @Override
  @NotNull
  public PsiModifierList getModifierList() {
    return myMethod.getModifierList();
  }

  @Override
  public PsiType getReturnType() {
    return mySubstitutor.substitute(myMethod.getReturnType());
  }

  @Override
  public PsiTypeElement getReturnTypeElement() {
    return myMethod.getReturnTypeElement();
  }

  @Override
  @NotNull
  public PsiParameterList getParameterList() {
    return mySubstitutor == PsiSubstitutor.EMPTY
           ? myMethod.getParameterList()
           : new LightParameterListWrapper(myMethod.getParameterList(), mySubstitutor);
  }

  @Override
  @NotNull
  public PsiReferenceList getThrowsList() {
    return myMethod.getThrowsList();
  }

  @Override
  public PsiCodeBlock getBody() {
    return myMethod.getBody();
  }

  @Override
  public boolean isConstructor() {
    return myMethod.isConstructor();
  }

  @Override
  public boolean isVarArgs() {
    return myMethod.isVarArgs();
  }

  @Override
  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return myMethod.getSignature(substitutor);
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return myMethod.getNameIdentifier();
  }

  @Override
  @NotNull
  public PsiMethod[] findSuperMethods() {
    return myMethod.findSuperMethods();
  }

  @Override
  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return myMethod.findSuperMethods(checkAccess);
  }

  @Override
  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return myMethod.findSuperMethods(parentClass);
  }

  @Override
  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return myMethod.findSuperMethodSignaturesIncludingStatic(checkAccess);
  }

  @Override
  @SuppressWarnings("deprecation")
  public PsiMethod findDeepestSuperMethod() {
    return myMethod.findDeepestSuperMethod();
  }

  @Override
  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return myMethod.findDeepestSuperMethods();
  }

  @Override
  public String getText() {
    return myMethod.getText();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    myMethod.accept(visitor);
  }

  @Override
  public PsiElement copy() {
    return new LightMethod(myManager, (PsiMethod)myMethod.copy(), myContainingClass);
  }

  @Override
  public boolean isValid() {
    return myContainingClass.isValid();
  }

  @NotNull
  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  @Override
  public PsiFile getContainingFile() {
    return myContainingClass.getContainingFile();
  }

  @Override
  public String toString() {
    return "Light PSI method wrapper:" + getName();
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
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
    return PsiImplUtil.getMemberUseScope(this);
  }

  @Override
  public PsiElement getContext() {
    return getContainingClass();
  }
}
