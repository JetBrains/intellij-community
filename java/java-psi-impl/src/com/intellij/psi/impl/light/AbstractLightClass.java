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

/*
 * @author max
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
  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
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
    return getDelegate().getSuperClass();
  }

  @Override
  public PsiClass[] getInterfaces() {
    return getDelegate().getInterfaces();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return getDelegate().getNavigationElement();
  }

  @Override
  @NotNull
  public PsiClass[] getSupers() {
    return getDelegate().getSupers();
  }

  @Override
  @NotNull
  public PsiClassType[] getSuperTypes() {
    return getDelegate().getSuperTypes();
  }

  @Override
  @NotNull
  public PsiField[] getFields() {
    return getDelegate().getFields();
  }

  @Override
  @NotNull
  public PsiMethod[] getMethods() {
    return getDelegate().getMethods();
  }

  @Override
  @NotNull
  public PsiMethod[] getConstructors() {
    return getDelegate().getConstructors();
  }

  @Override
  @NotNull
  public PsiClass[] getInnerClasses() {
    return getDelegate().getInnerClasses();
  }

  @Override
  @NotNull
  public PsiClassInitializer[] getInitializers() {
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
  @NotNull
  public PsiField[] getAllFields() {
    return getDelegate().getAllFields();
  }

  @Override
  @NotNull
  public PsiMethod[] getAllMethods() {
    return getDelegate().getAllMethods();
  }

  @Override
  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return getDelegate().getAllInnerClasses();
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
  public boolean isInheritorDeep(PsiClass baseClass, @Nullable PsiClass classToByPass) {
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
