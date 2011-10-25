/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class LightClass extends LightElement implements PsiClass {
  protected final PsiClass myDelegate;

  public LightClass(PsiClass delegate) {
    this(delegate, JavaLanguage.INSTANCE);
  }

  public LightClass(PsiClass delegate, final Language language) {
    super(delegate.getManager(), language);
    myDelegate = delegate;
  }

  @Override
  @NonNls
  @Nullable
  public String getName() {
    return myDelegate.getName();
  }

  @Override
  @Nullable
  public PsiModifierList getModifierList() {
    return myDelegate.getModifierList();
  }

  @Override
  public boolean hasModifierProperty(@Modifier @NonNls @NotNull String name) {
    return myDelegate.hasModifierProperty(name);
  }

  @Override
  @Nullable
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return myDelegate.isDeprecated();
  }

  @Override
  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @Override
  @Nullable
  public PsiTypeParameterList getTypeParameterList() {
    return myDelegate.getTypeParameterList();
  }

  @Override
  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return myDelegate.getTypeParameters();
  }

  @Override
  @NonNls
  @Nullable
  public String getQualifiedName() {
    return myDelegate.getQualifiedName();
  }

  @Override
  public boolean isInterface() {
    return myDelegate.isInterface();
  }

  @Override
  public boolean isAnnotationType() {
    return myDelegate.isAnnotationType();
  }

  @Override
  public boolean isEnum() {
    return myDelegate.isEnum();
  }

  @Override
  @Nullable
  public PsiReferenceList getExtendsList() {
    return myDelegate.getExtendsList();
  }

  @Override
  @Nullable
  public PsiReferenceList getImplementsList() {
    return myDelegate.getImplementsList();
  }

  @Override
  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  @Override
  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassImplUtil.getImplementsListTypes(this);
  }

  @Override
  @Nullable
  public PsiClass getSuperClass() {
    return myDelegate.getSuperClass();
  }

  @Override
  public PsiClass[] getInterfaces() {
    return myDelegate.getInterfaces();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myDelegate.getNavigationElement();
  }

  @Override
  @NotNull
  public PsiClass[] getSupers() {
    return myDelegate.getSupers();
  }

  @Override
  @NotNull
  public PsiClassType[] getSuperTypes() {
    return myDelegate.getSuperTypes();
  }

  @Override
  @NotNull
  public PsiField[] getFields() {
    return myDelegate.getFields();
  }

  @Override
  @NotNull
  public PsiMethod[] getMethods() {
    return myDelegate.getMethods();
  }

  @Override
  @NotNull
  public PsiMethod[] getConstructors() {
    return myDelegate.getConstructors();
  }

  @Override
  @NotNull
  public PsiClass[] getInnerClasses() {
    return myDelegate.getInnerClasses();
  }

  @Override
  @NotNull
  public PsiClassInitializer[] getInitializers() {
    return myDelegate.getInitializers();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, false);
  }

  @Override
  @NotNull
  public PsiField[] getAllFields() {
    return myDelegate.getAllFields();
  }

  @Override
  @NotNull
  public PsiMethod[] getAllMethods() {
    return myDelegate.getAllMethods();
  }

  @Override
  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return myDelegate.getAllInnerClasses();
  }

  @Override
  @Nullable
  public PsiField findFieldByName(@NonNls String name, boolean checkBases) {
    return PsiClassImplUtil.findFieldByName(this, name, checkBases);
  }

  @Override
  @Nullable
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  @NotNull
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiMethod.class);
  }

  @Override
  @Nullable
  public PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
    return myDelegate.findInnerClassByName(name, checkBases);
  }

  @Override
  @Nullable
  public PsiElement getLBrace() {
    return myDelegate.getLBrace();
  }

  @Override
  @Nullable
  public PsiElement getRBrace() {
    return myDelegate.getRBrace();
  }

  @Override
  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return myDelegate.getNameIdentifier();
  }

  @Override
  public PsiElement getScope() {
    return myDelegate.getScope();
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return myDelegate.isInheritor(baseClass, checkDeep);
  }

  @Override
  public boolean isInheritorDeep(PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return myDelegate.isInheritorDeep(baseClass, classToByPass);
  }

  @Override
  @Nullable
  public PsiClass getContainingClass() {
    return myDelegate.getContainingClass();
  }

  @Override
  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return myDelegate.getVisibleSignatures();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    return myDelegate.setName(name);
  }

  @Override
  public String toString() {
    return "PsiClass:" + getName();
  }

  @Override
  public String getText() {
    return myDelegate.getText();
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
  public PsiElement copy() {
    return new LightClass(this);
  }

  @Override
  public PsiFile getContainingFile() {
    return myDelegate.getContainingFile();
  }

  public PsiClass getDelegate() {
    return myDelegate;
  }

  @Override
  public PsiElement getContext() {
    return myDelegate;
  }

  @Override
  public boolean isValid() {
    return myDelegate.isValid();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return this == another ||
           (another instanceof LightClass && getDelegate().isEquivalentTo(((LightClass)another).getDelegate())) ||
           getDelegate().isEquivalentTo(another);
  }
}
