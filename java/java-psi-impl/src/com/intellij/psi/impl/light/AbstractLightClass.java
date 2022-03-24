// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class AbstractLightClass extends LightElement implements PsiClass {
  protected AbstractLightClass(PsiManager manager, Language language) {
    super(manager, language);
  }

  protected AbstractLightClass(PsiManager manager) {
    super(manager, JavaLanguage.INSTANCE);
  }

  @NotNull
  public abstract PsiClass getDelegate();

  @Override
  @NotNull
  public abstract PsiElement copy();

  @Override
  @NonNls
  @Nullable
  public String getName() {
    return getDelegate().getName();
  }

  @Override
  @Nullable
  public PsiModifierList getModifierList() {
    return getDelegate().getModifierList();
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return getDelegate().hasModifierProperty(name);
  }

  @Override
  @Nullable
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return getDelegate().isDeprecated();
  }

  @Override
  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @Override
  @Nullable
  public PsiTypeParameterList getTypeParameterList() {
    return getDelegate().getTypeParameterList();
  }

  @Override
  public PsiTypeParameter @NotNull [] getTypeParameters() {
    return getDelegate().getTypeParameters();
  }

  @Override
  @NonNls
  @Nullable
  public String getQualifiedName() {
    return getDelegate().getQualifiedName();
  }

  @Override
  public boolean isInterface() {
    return getDelegate().isInterface();
  }

  @Override
  public boolean isAnnotationType() {
    return getDelegate().isAnnotationType();
  }

  @Override
  public boolean isEnum() {
    return getDelegate().isEnum();
  }

  @Override
  @Nullable
  public PsiReferenceList getExtendsList() {
    return getDelegate().getExtendsList();
  }

  @Override
  @Nullable
  public PsiReferenceList getImplementsList() {
    return getDelegate().getImplementsList();
  }

  @Override
  public PsiClassType @NotNull [] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  @Override
  public PsiClassType @NotNull [] getImplementsListTypes() {
    return PsiClassImplUtil.getImplementsListTypes(this);
  }

  @Override
  @Nullable
  public PsiClass getSuperClass() {
    return getDelegate().getSuperClass();
  }

  @Override
  public PsiClass @NotNull [] getInterfaces() {
    return getDelegate().getInterfaces();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return getDelegate().getNavigationElement();
  }

  @Override
  public PsiClass @NotNull [] getSupers() {
    return getDelegate().getSupers();
  }

  @Override
  public PsiClassType @NotNull [] getSuperTypes() {
    return getDelegate().getSuperTypes();
  }

  @Override
  public PsiField @NotNull [] getFields() {
    return getDelegate().getFields();
  }

  @Override
  public PsiMethod @NotNull [] getMethods() {
    return getDelegate().getMethods();
  }

  @Override
  public PsiMethod @NotNull [] getConstructors() {
    return getDelegate().getConstructors();
  }

  @Override
  public PsiClass @NotNull [] getInnerClasses() {
    return getDelegate().getInnerClasses();
  }

  @Override
  public PsiClassInitializer @NotNull [] getInitializers() {
    return getDelegate().getInitializers();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, PsiUtil.getLanguageLevel(place), false);
  }

  @Override
  public PsiField @NotNull [] getAllFields() {
    return getDelegate().getAllFields();
  }

  @Override
  public PsiMethod @NotNull [] getAllMethods() {
    return getDelegate().getAllMethods();
  }

  @Override
  public PsiClass @NotNull [] getAllInnerClasses() {
    return getDelegate().getAllInnerClasses();
  }

  @Override
  @Nullable
  public PsiField findFieldByName(@NonNls String name, boolean checkBases) {
    return PsiClassImplUtil.findFieldByName(this, name, checkBases);
  }

  @Override
  @Nullable
  public PsiMethod findMethodBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsByName(@NonNls String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls @NotNull String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiClassImplUtil.MemberType.METHOD);
  }

  @Override
  @Nullable
  public PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
    return getDelegate().findInnerClassByName(name, checkBases);
  }

  @Override
  @Nullable
  public PsiElement getLBrace() {
    return getDelegate().getLBrace();
  }

  @Override
  @Nullable
  public PsiElement getRBrace() {
    return getDelegate().getRBrace();
  }

  @Override
  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return getDelegate().getNameIdentifier();
  }

  @Override
  public PsiElement getScope() {
    return getDelegate().getScope();
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return getDelegate().isInheritor(baseClass, checkDeep);
  }

  @Override
  public boolean isInheritorDeep(@NotNull PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return getDelegate().isInheritorDeep(baseClass, classToByPass);
  }

  @Override
  @Nullable
  public PsiClass getContainingClass() {
    return getDelegate().getContainingClass();
  }

  @Override
  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return getDelegate().getVisibleSignatures();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    return getDelegate().setName(name);
  }

  @Override
  public String toString() {
    return "PsiClass:" + getName();
  }

  @Override
  public String getText() {
    return getDelegate().getText();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClass(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiFile getContainingFile() {
    return getDelegate().getContainingFile();
  }

  @Override
  public PsiElement getContext() {
    return getDelegate();
  }

  @Override
  public boolean isValid() {
    return getDelegate().isValid();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return this == another ||
           (another instanceof AbstractLightClass && getDelegate().isEquivalentTo(((AbstractLightClass)another).getDelegate())) ||
           getDelegate().isEquivalentTo(another);
  }

}
