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
package com.intellij.psi.impl.migration;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author dsl
 */
public class MigrationClassImpl extends LightElement implements PsiClass{
  private final String myQualifiedName;
  private final String myName;
  private final PsiMigrationImpl myMigration;

  MigrationClassImpl(PsiMigrationImpl migration, String qualifiedName) {
    super(migration.getManager(), JavaLanguage.INSTANCE);
    myMigration = migration;
    myQualifiedName = qualifiedName;
    myName = PsiNameHelper.getShortClassName(myQualifiedName);
  }

  public String toString() {
    return "MigrationClass:" + myQualifiedName;
  }

  @Override
  public String getText() {
    return "class " + myName + " {}";
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClass(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiElement copy() {
    return new MigrationClassImpl(myMigration, myQualifiedName);
  }

  @Override
  public boolean isValid() {
    return myMigration.isValid();
  }

  @Override
  public String getQualifiedName() {
    return myQualifiedName;
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

  @Override
  public PsiReferenceList getExtendsList() {
    return null;
  }


  @Override
  public PsiReferenceList getImplementsList() {
    return null;
  }

  @Override
  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  public PsiClass getSuperClass() {
    return JavaPsiFacade.getInstance(myManager.getProject())
      .findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(myManager.getProject()));
  }

  @Override
  public PsiClass[] getInterfaces() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiClass[] getSupers() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiClassType[] getSuperTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @Override
  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return Collections.emptySet();
  }

  @Override
  @NotNull
  public PsiField[] getFields() {
    return PsiField.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiMethod[] getMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiMethod[] getConstructors() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiClass[] getInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiField[] getAllFields() {
    return PsiField.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiMethod[] getAllMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  public PsiField findFieldByName(String name, boolean checkBases) {
    return null;
  }

  @Override
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return null;
  }

  @Override
  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return new ArrayList<>();
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return new ArrayList<>();
  }

  @Override
  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return null;
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @Override
  public boolean hasTypeParameters() {
    return false;
  }

  @Override
  public PsiJavaToken getLBrace() {
    return null;
  }

  @Override
  public PsiJavaToken getRBrace() {
    return null;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  // very special method!
  @Override
  public PsiElement getScope() {
    return null;
  }

  @Override
  public boolean isInheritorDeep(PsiClass baseClass, PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return PsiModifier.PUBLIC.equals(name);
  }

  @Override
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  public PsiMetaData getMetaData() {
    return null;
  }

  @Override
  public Icon getElementIcon(final int flags) {
    return PsiClassImplUtil.getClassIcon(flags, this);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(this, another);
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    return PsiClassImplUtil.getClassUseScope(this);
  }
}
