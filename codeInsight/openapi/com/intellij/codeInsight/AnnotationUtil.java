/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class AnnotationUtil {
  /**
   * The full qualified name of the standard Nullable annotation.
   */
  public static final String NULLABLE = "org.jetbrains.annotations.Nullable";

  /**
   * The full qualified name of the standard NotNull annotation.
   */
  public static final String NOT_NULL = "org.jetbrains.annotations.NotNull";

  /**
   * The full qualified name of the standard NonNls annotation.
   * @since 5.0.1
   */
  public static final String NON_NLS = "org.jetbrains.annotations.NonNls";
  public static final String PROPERTY_KEY = "org.jetbrains.annotations.PropertyKey";
  @NonNls public static final String PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER = "resourceBundle";

  private final static Set<String> ALL_ANNOTATIONS;

  static {
    ALL_ANNOTATIONS = new HashSet<String>(2);
    ALL_ANNOTATIONS.add(NULLABLE);
    ALL_ANNOTATIONS.add(NOT_NULL);
  }

  public static boolean isNullable(PsiModifierListOwner owner) {
    if (isNotNull(owner)) return false;
    final PsiAnnotation ann = findAnnotationInHierarchy(owner, ALL_ANNOTATIONS);
    return ann != null && NULLABLE.equals(ann.getQualifiedName());
  }

  public static boolean isNotNull(PsiModifierListOwner owner) {
    final PsiAnnotation ann = findAnnotationInHierarchy(owner, ALL_ANNOTATIONS);
    return ann != null && NOT_NULL.equals(ann.getQualifiedName());
  }

  @Nullable
  public static PsiAnnotation findAnnotation(PsiModifierListOwner listOwner, Set<String> annotationNames) {
    final PsiModifierList list = listOwner.getModifierList();
    final PsiAnnotation[] allAnnotations = list.getAnnotations();
    for (PsiAnnotation annotation : allAnnotations) {
      String qualifiedName = annotation.getQualifiedName();
      if (annotationNames.contains(qualifiedName)) {
        return annotation;
      }
    }
    return null;
  }

  @Nullable
  public static PsiAnnotation findAnnotationInHierarchy(PsiModifierListOwner listOwner, Set<String> annotationNames) {
    PsiAnnotation directAnnotation = findAnnotation(listOwner, annotationNames);
    if (directAnnotation != null) return directAnnotation;

    if (!(listOwner instanceof PsiMethod)) {
      return null;
    }
    PsiMethod method = (PsiMethod)listOwner;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    HierarchicalMethodSignature methodSignature = method.getHierarchicalMethodSignature();
    return findAnnotationInHierarchy(methodSignature, annotationNames, method);
  }

  private static PsiAnnotation findAnnotationInHierarchy(HierarchicalMethodSignature signature, Set<String> annotationNames, PsiElement place) {
    List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();
    PsiResolveHelper resolveHelper = place.getManager().getResolveHelper();
    for (HierarchicalMethodSignature superSignature : superSignatures) {
      PsiMethod superMethod = superSignature.getMethod();
      if (!resolveHelper.isAccessible(superMethod, place, null)) continue;
      PsiAnnotation direct = findAnnotation(superMethod, annotationNames);
      if (direct != null) return direct;
      PsiAnnotation superResult = findAnnotationInHierarchy(superSignature, annotationNames, place);
      if (superResult != null) return superResult;
    }

    return null;
  }

  public static boolean isAnnotated(@NotNull PsiModifierListOwner listOwner, String annotationFQN, boolean checkHierarchy) {
    if (listOwner instanceof PsiParameter) {
      // this is more efficient than getting the modifier list
      PsiAnnotation[] paramAnnotations = ((PsiParameter)listOwner).getAnnotations();
      for(PsiAnnotation annotation: paramAnnotations) {
        if (annotationFQN.equals(annotation.getQualifiedName())) {
          return true;
        }
      }
      return false;
    }
    final PsiModifierList modifierList = listOwner.getModifierList();
    if (modifierList == null) {
      return false;
    }
    PsiAnnotation annotation = modifierList.findAnnotation(annotationFQN);
    if (annotation != null) return true;
    if (checkHierarchy && listOwner instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)listOwner;
      final PsiMethod[] superMethods = method.findSuperMethods();
      for (PsiMethod superMethod : superMethods) {
        if (isAnnotated(superMethod, annotationFQN, checkHierarchy)) return true;
      }
    }
    return false;
  }

  public static boolean isAnnotatingApplicable(PsiElement elt) {
    if (PsiUtil.getLanguageLevel(elt).compareTo(LanguageLevel.JDK_1_5) < 0) return false;
    return elt.getManager().findClass(NULLABLE, elt.getResolveScope()) != null;
  }
}
