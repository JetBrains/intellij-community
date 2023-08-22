/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 * according to JShell spec, a snippet must correspond to one of the following JLS syntax productions:
      Expression
      Statement
      ClassDeclaration
      InterfaceDeclaration
      MethodDeclaration
      FieldDeclaration
      ImportDeclaration
 */
public class PsiJShellRootClassImpl extends ASTWrapperPsiElement implements PsiJShellRootClass {

  private final String myName;
  private final String myQName;

  public PsiJShellRootClassImpl(ASTNode node, int index) {
    super(node);
    myName = "$$jshell_root_class$$" + index;
    myQName = "REPL." + myName;
  }

  @Override
  public PsiJShellImportHolder[] getSnippets() {
    return findChildren(PsiJShellImportHolder.class, PsiJShellImportHolder.EMPTY_ARRAY);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    LanguageLevel level = PsiUtil.getLanguageLevel(place);
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, level, false);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return myQName;
  }

  @Override
  public boolean isInterface() {
    return false;
  }

  @Override
  public boolean isAnnotationType() {
    return false;
  }

  @Override
  public boolean isEnum() {
    return false;
  }

  @Nullable
  @Override
  public PsiReferenceList getExtendsList() {
    return null;
  }

  @Nullable
  @Override
  public PsiReferenceList getImplementsList() {
    return null;
  }

  @Override
  public PsiClassType @NotNull [] getExtendsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  public PsiClassType @NotNull [] getImplementsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public PsiClass getSuperClass() {
    return null;
  }

  @Override
  public PsiClass @NotNull [] getInterfaces() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  public PsiClass @NotNull [] getSupers() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  public PsiClassType @NotNull [] getSuperTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  public PsiField @NotNull [] getFields() {
    return findChildren(PsiField.class, PsiField.EMPTY_ARRAY);
  }

  @Override
  public PsiMethod @NotNull [] getMethods() {
    return findChildren(PsiMethod.class, PsiMethod.EMPTY_ARRAY);
  }

  @Override
  public PsiMethod @NotNull [] getConstructors() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  public PsiClass @NotNull [] getInnerClasses() {
    return findChildren(PsiClass.class, PsiClass.EMPTY_ARRAY);
  }

  @Override
  public PsiClassInitializer @NotNull [] getInitializers() {
    return findChildren(PsiClassInitializer.class, PsiClassInitializer.EMPTY_ARRAY);
  }

  @Override
  public PsiField @NotNull [] getAllFields() {
    return PsiClassImplUtil.getAllFields(this);
  }

  @Override
  public PsiMethod @NotNull [] getAllMethods() {
    return PsiClassImplUtil.getAllMethods(this);
  }

  @Override
  public PsiClass @NotNull [] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  @Nullable
  @Override
  public PsiField findFieldByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findFieldByName(this, name, checkBases);
  }

  @Nullable
  @Override
  public PsiMethod findMethodBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsBySignature(@NotNull PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiMethod @NotNull [] findMethodsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @NotNull
  @Override
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NotNull String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @NotNull
  @Override
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiClassImplUtil.MemberType.METHOD);
  }

  @Nullable
  @Override
  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findInnerByName(this, name, checkBases);
  }

  @Nullable
  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public PsiElement getScope() {
    return getContainingFile();
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Override
  public boolean isInheritorDeep(@NotNull PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  @Override
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Nullable
  @Override
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean hasTypeParameters() {
    return false;
  }

  @Nullable
  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @Override
  public PsiTypeParameter @NotNull [] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return PsiModifier.PUBLIC.equals(name) || PsiModifier.FINAL.equals(name);
  }

  @Nullable
  @Override
  public PsiJavaToken getLBrace() {
    return null;
  }

  @Nullable
  @Override
  public PsiJavaToken getRBrace() {
    return null;
  }

  private <T extends PsiElement> T @NotNull [] findChildren(Class<T> memberClass, T[] emptyArray) {
    T[] members = PsiTreeUtil.getChildrenOfType(this, memberClass);
    return members != null ? members : emptyArray;
  }
}
