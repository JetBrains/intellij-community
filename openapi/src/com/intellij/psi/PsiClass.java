/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.PomMemberOwner;
import com.intellij.psi.meta.PsiMetaOwner;

import java.util.List;


public interface PsiClass extends PsiElement, PsiNamedElement, PsiModifierListOwner, PsiDocCommentOwner, PsiMetaOwner, PsiTypeParameterListOwner, PsiMember {
  PsiClass[] EMPTY_ARRAY  = new PsiClass[0];

  /**
   * @MaybeNull(description = "return null for anonymous and local classes, and for type parameters")
   */
  String getQualifiedName();

  boolean isInterface();
  boolean isAnnotationType();
  boolean isEnum();

  /**
   *
   * @MaybeNull(description = "return null for anonymous classes, enums and annotation types")
   */
  PsiReferenceList getExtendsList();

  /**
   *
   * @MaybeNull(description = "return null for anonymous classes")
   */
  PsiReferenceList getImplementsList();

  PsiClassType[] getExtendsListTypes();
  PsiClassType[] getImplementsListTypes();

  /**
   * @MaybeNull(description = "May return null when jdk is not configured, so no java.lang.Object is found, or for java.lang.Object itself")
   */
  PsiClass getSuperClass();

  PsiClass[] getInterfaces();

  /**
   * @AtLeast(elements = 0, description = "May return zero elements when jdk is not configured, so no java.lang.Object is found")
   */
  PsiClass[] getSupers();

  /**
   * @AtLeast(elements = 1, description = "At least type of java.lang.Object is returned")
   */
  PsiClassType[] getSuperTypes();

  PsiField[] getFields();
  PsiMethod[] getMethods();
  PsiMethod[] getConstructors();
  PsiClass[] getInnerClasses();
  PsiClassInitializer[] getInitializers();

  PsiTypeParameter[] getTypeParameters();

  PsiField[] getAllFields();
  PsiMethod[] getAllMethods();
  PsiClass[] getAllInnerClasses();

  PsiField    findFieldByName(String name, boolean checkBases);
  PsiMethod   findMethodBySignature(PsiMethod patternMethod, boolean checkBases);
  PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases);
  PsiMethod[] findMethodsByName(String name, boolean checkBases);
  List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases);
  List<Pair<PsiMethod,PsiSubstitutor>> getAllMethodsAndTheirSubstitutors();

  PsiClass findInnerClassByName(String name, boolean checkBases);

  PsiJavaToken getLBrace();
  PsiJavaToken getRBrace();

  /**
   *
   * @MaybeNull(description = "parser understands classes without name identifiers")
   */
  PsiIdentifier getNameIdentifier();

  // very special method!
  PsiElement getScope();

  boolean isInheritor(PsiClass baseClass, boolean checkDeep);

  PomMemberOwner getPom();
}
