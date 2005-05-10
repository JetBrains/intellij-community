package com.intellij.codeInsight;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author max
 */
public class AnnotationUtil {
  public static final String NULLABLE = "org.jetbrains.annotations.Nullable";
  public static final String NOT_NULL = "org.jetbrains.annotations.NotNull";
  private final static Set<String> ALL_ANNOTATIONS;

  static {
    ALL_ANNOTATIONS = new HashSet<String>(2);
    ALL_ANNOTATIONS.add(NULLABLE);
    ALL_ANNOTATIONS.add(NOT_NULL);
  }

  public static boolean isNullable(PsiModifierListOwner owner) {
    final PsiAnnotation ann = findAnnotation(owner, ALL_ANNOTATIONS);
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
      final PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
      if (nameRef != null && annotationNames.contains(nameRef.getCanonicalText())) {
        return annotation;
      }
    }
    return null;
  }

  @Nullable
  public static PsiAnnotation findAnnotationInHierarchy(PsiModifierListOwner listOwner, Set<String> annotationNames) {
    PsiAnnotation directAnnotation = findAnnotation(listOwner, annotationNames);
    if (directAnnotation != null) return directAnnotation;

    if (listOwner instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)listOwner;
      final PsiMethod[] superMethods = method.findSuperMethods();
      for (PsiMethod superMethod : superMethods) {
        final PsiAnnotation derivedAnnotation = findAnnotationInHierarchy(superMethod, annotationNames);
        if (derivedAnnotation != null) return derivedAnnotation;
      }
    }

    return null;
  }

  public static boolean isAnnotatingApplicable(PsiElement elt) {
    final PsiManager manager = elt.getManager();
    if (manager.getEffectiveLanguageLevel() != LanguageLevel.JDK_1_5) return false;
    return manager.findClass(NULLABLE, elt.getResolveScope()) != null;
  }
}
