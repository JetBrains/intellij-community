// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/// Finds container nullability annotations, such as [org.jspecify.annotations.NullMarked] or
/// [NotNullByDefault], that apply to a signature element.
@NotNullByDefault
final class JavaDocNullabilityUtil {
  private JavaDocNullabilityUtil() {
  }

  static @Nullable AnnotationDocGenerator findContainerAnnotation(PsiModifierListOwner owner,
                                                                  List<AnnotationDocGenerator> existingAnnotations) {
    var manager = NullableNotNullManager.getInstance(owner.getProject());
    var annotation = switch (owner) {
      case PsiMethod method when method.getReturnType() == null || method.getReturnType() instanceof PsiPrimitiveType ->
        findContainerAnnotationForComponents(manager, method, method.getParameterList().getParameters());
      case PsiClass psiClass when psiClass.isRecord() ->
        findContainerAnnotationForComponents(manager, psiClass, psiClass.getRecordComponents());
      case PsiMethod _, PsiVariable _ -> findEffectiveContainerAnnotation(manager, owner);
      default -> null;
    };
    if (annotation == null) return null;

    var shownAnnotations =
      existingAnnotations.stream().map(existingAnnotation -> existingAnnotation.getAnnotationQualifiedName()).collect(Collectors.toSet());
    return AnnotationDocGenerator.forAnnotation(owner, shownAnnotations, annotation, true);
  }

  private static @Nullable PsiAnnotation findEffectiveContainerAnnotation(NullableNotNullManager manager,
                                                                          PsiModifierListOwner owner) {
    var info = manager.findEffectiveNullabilityInfo(owner);
    if (info != null && info.getNullability() == Nullability.NOT_NULL && info.isContainer()) {
      return info.getAnnotation();
    }
    return null;
  }

  /// @return a container [PsiAnnotation] if it affects a component.
  ///
  /// Affected components method parameters/record components which have no nullability annotation of their own (and are not primitive types)
  private static @Nullable PsiAnnotation findContainerAnnotationForComponents(NullableNotNullManager manager,
                                                                              PsiModifierListOwner owner,
                                                                              PsiVariable[] components) {
    var info = manager.findContainerAnnotation(owner);
    if (info == null || info.getNullability() != Nullability.NOT_NULL) return null;
    return ContainerUtil.exists(components, component -> {
      if (component.getType() instanceof PsiPrimitiveType) return false;
      var paramInfo = manager.findEffectiveNullabilityInfo(component);
      return paramInfo != null && paramInfo.getAnnotation().equals(info.getAnnotation()) && !paramInfo.isInferred();
    }) ? info.getAnnotation() : null;
  }
}
