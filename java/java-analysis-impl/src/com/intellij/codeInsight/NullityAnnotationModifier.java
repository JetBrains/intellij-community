// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiType;
import com.intellij.psi.TypeAnnotationProvider;
import com.intellij.psi.augment.TypeAnnotationModifier;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public class NullityAnnotationModifier extends TypeAnnotationModifier {
  @Nullable
  @Override
  public TypeAnnotationProvider boundAppeared(@NotNull PsiType inferenceVariableType, @NotNull PsiType boundType) {
    PsiAnnotation[] annotations = inferenceVariableType.getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      if (isMatchingNullityAnnotation(boundType, annotation)) {
        return removeAnnotation(annotations, annotation);
      }
    }

    return null;
  }

  private static boolean isMatchingNullityAnnotation(@NotNull PsiType boundType, PsiAnnotation annotation) {
    String qName = annotation.getQualifiedName();
    return qName != null &&
           (NullableNotNullManager.isNullableAnnotation(annotation) || NullableNotNullManager.isNotNullAnnotation(annotation)) &&
           boundType.hasAnnotation(qName);
  }

  @Nullable
  @Override
  public TypeAnnotationProvider modifyLowerBoundAnnotations(@NotNull PsiType lowerBound, @NotNull PsiType upperBound) {
    PsiAnnotation[] lowerAnnotations = lowerBound.getAnnotations();
    PsiAnnotation nullable = findNullable(lowerAnnotations);
    if (nullable != null && findNullable(upperBound.getAnnotations()) == null) {
      return removeAnnotation(lowerAnnotations, nullable);
    }
    return null;
  }

  private static PsiAnnotation findNullable(PsiAnnotation[] annotations) {
    return ContainerUtil.find(annotations, NullableNotNullManager::isNullableAnnotation);
  }

  @NotNull
  private static TypeAnnotationProvider removeAnnotation(PsiAnnotation[] annotations, PsiAnnotation annotation) {
    List<PsiAnnotation> list = ContainerUtil.newArrayList(annotations);
    list.remove(annotation);
    return TypeAnnotationProvider.Static.create(list.toArray(PsiAnnotation.EMPTY_ARRAY));
  }
}
