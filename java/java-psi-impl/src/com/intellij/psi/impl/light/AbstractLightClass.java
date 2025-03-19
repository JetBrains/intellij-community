// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

public abstract class AbstractLightClass extends LightElement implements PsiClass, SyntheticElement {
  protected AbstractLightClass(PsiManager manager, Language language) {
    super(manager, language);
  }

  protected AbstractLightClass(PsiManager manager) {
    super(manager, JavaLanguage.INSTANCE);
  }

  public abstract @NotNull PsiClass getDelegate();

  @Override
  public abstract @NotNull PsiElement copy();

  @Override
  public @NonNls @Nullable String getName() {
    return getDelegate().getName();
  }

  @Override
  public @Nullable PsiModifierList getModifierList() {
    return getDelegate().getModifierList();
  }

  @Override
  public boolean hasModifierProperty(@NonNls @NotNull String name) {
    return getDelegate().hasModifierProperty(name);
  }

  @Override
  public @Nullable PsiDocComment getDocComment() {
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
  public @Nullable PsiTypeParameterList getTypeParameterList() {
    return getDelegate().getTypeParameterList();
  }

  @Override
  public PsiTypeParameter @NotNull [] getTypeParameters() {
    return getDelegate().getTypeParameters();
  }

  @Override
  public @NonNls @Nullable String getQualifiedName() {
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
  public @Nullable PsiReferenceList getExtendsList() {
    return getDelegate().getExtendsList();
  }

  @Override
  public @Nullable PsiReferenceList getPermitsList() {
    return getDelegate().getPermitsList();
  }

  @Override
  public @Nullable PsiReferenceList getImplementsList() {
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
  public @Nullable PsiClass getSuperClass() {
    return getDelegate().getSuperClass();
  }

  @Override
  public PsiClass @NotNull [] getInterfaces() {
    return getDelegate().getInterfaces();
  }

  @Override
  public @NotNull PsiElement getNavigationElement() {
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
  public @Nullable PsiField findFieldByName(@NonNls String name, boolean checkBases) {
    return PsiClassImplUtil.findFieldByName(this, name, checkBases);
  }

  @Override
  public @Nullable PsiMethod findMethodBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
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
  public @Unmodifiable @NotNull List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls @NotNull String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Override
  public @Unmodifiable @NotNull List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiClassImplUtil.MemberType.METHOD);
  }

  @Override
  public @Nullable PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
    return getDelegate().findInnerClassByName(name, checkBases);
  }

  @Override
  public @Nullable PsiElement getLBrace() {
    return getDelegate().getLBrace();
  }

  @Override
  public @Nullable PsiElement getRBrace() {
    return getDelegate().getRBrace();
  }

  @Override
  public @Nullable PsiIdentifier getNameIdentifier() {
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
  public @Nullable PsiClass getContainingClass() {
    return getDelegate().getContainingClass();
  }

  @Override
  public @NotNull Collection<HierarchicalMethodSignature> getVisibleSignatures() {
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

  @Override
  public @NotNull PsiElement findSameElementInCopy(@NotNull PsiFile copy) {
    PsiElement parent = getParent();
    if (parent instanceof PsiClassOwner && copy instanceof PsiClassOwner) {
      PsiClass[] copyClasses = ((PsiClassOwner)copy).getClasses();
      for (PsiClass copyClass : copyClasses) {
        if (copyClass.isEquivalentTo(this)) {
          return copyClass;
        }
      }
    }
    return SyntheticElement.super.findSameElementInCopy(copy);
  }
}
