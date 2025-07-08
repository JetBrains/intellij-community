// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.codeInsight.*;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Helper methods to compute nullability of Java types.
 */
@ApiStatus.Internal
public final class JavaTypeNullabilityUtil {
  /**
   * Computes the class type nullability
   * 
   * @param type type to compute nullability for
   * @return type nullability
   */
  public static @NotNull TypeNullability getTypeNullability(@NotNull PsiClassType type) {
    return getTypeNullability(type, null, !isLocal(type));
  }
  
  private static @NotNull TypeNullability getTypeNullability(@NotNull PsiClassType type,
                                                             @Nullable Set<PsiClassType> visited,
                                                             boolean checkContainer) {
    if (visited != null && visited.contains(type)) return TypeNullability.UNKNOWN;
    TypeNullability fromAnnotations = getNullabilityFromAnnotations(type.getAnnotations());
    if (!fromAnnotations.equals(TypeNullability.UNKNOWN)) return fromAnnotations;
    PsiElement context = type.getPsiContext();
    if (context != null && checkContainer) {
      NullableNotNullManager manager = NullableNotNullManager.getInstance(context.getProject());
      if (manager != null) {
        NullabilityAnnotationInfo typeUseNullability = manager.findDefaultTypeUseNullability(context);
        if (typeUseNullability != null) {
          return typeUseNullability.toTypeNullability();
        }
      }
    }
    PsiClass target = type.resolve();
    if (target instanceof PsiTypeParameter) {
      PsiTypeParameter typeParameter = (PsiTypeParameter)target;
      PsiReferenceList extendsList = typeParameter.getExtendsList();
      PsiClassType[] extendTypes = extendsList.getReferencedTypes();
      if (extendTypes.length == 0 && checkContainer) {
        NullableNotNullManager manager = NullableNotNullManager.getInstance(typeParameter.getProject());
        // If there's no bound, we assume an implicit `extends Object` bound, which is subject to default annotation if any.
        NullabilityAnnotationInfo typeUseNullability = manager == null ? null : manager.findDefaultTypeUseNullability(typeParameter);
        if (typeUseNullability != null) {
          return typeUseNullability.toTypeNullability().inherited();
        }
      } else {
        Set<PsiClassType> nextVisited = visited == null ? new HashSet<>() : visited;
        nextVisited.add(type);
        List<TypeNullability> nullabilities = ContainerUtil.map(extendTypes, t -> getTypeNullability(t, nextVisited, checkContainer));
        return TypeNullability.intersect(nullabilities).inherited();
      }
    }
    return TypeNullability.UNKNOWN;
  }
  
  private static boolean isLocal(PsiClassType classType) {
    PsiElement context = classType.getPsiContext();
    return context instanceof PsiJavaCodeReferenceElement &&
           context.getParent() instanceof PsiTypeElement &&
           context.getParent().getParent() instanceof PsiLocalVariable;
  }

  /**
   * Computes the nullability from explicit annotations
   * @param annotations array of annotations to look for nullability annotations in
   * @return nullability from explicit annotations
   */
  public static @NotNull TypeNullability getNullabilityFromAnnotations(@NotNull PsiAnnotation @NotNull [] annotations) {
    for (PsiAnnotation annotation : annotations) {
      String qualifiedName = annotation.getQualifiedName();
      NullableNotNullManager manager = NullableNotNullManager.getInstance(annotation.getProject());
      if (manager == null) return TypeNullability.UNKNOWN;
      Optional<Nullability> optionalNullability = manager.getAnnotationNullability(qualifiedName);
      if (optionalNullability.isPresent()) {
        Nullability nullability = optionalNullability.get();
        return new TypeNullability(nullability, new NullabilitySource.ExplicitAnnotation(annotation));
      }
    }
    return TypeNullability.UNKNOWN;
  }
}
