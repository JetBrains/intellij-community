// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.TypeNullability;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.JavaTypeNullabilityUtil;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.AnnotationUtil.getRelatedType;
import static com.intellij.util.ObjectUtils.tryCast;

/// Encapsulates information related to a nullability annotation.
///
@NotNullByDefault
public final class NullabilityAnnotationWrapper {
  private final NullabilityAnnotationInfo info;

  private NullabilityAnnotationWrapper(NullabilityAnnotationInfo info) {
    this.info = info;
  }

  /// Constructs a NullabilityAnnotationWrapper for a given PsiAnnotation
  ///
  /// @return a NullabilityAnnotationWrapper instance containing nullability and annotation details,
  ///         or null if the annotation's nullability cannot be determined
  public static @Nullable NullabilityAnnotationWrapper from(PsiAnnotation annotation) {
    TypeNullability typeNullability = JavaTypeNullabilityUtil.getNullabilityFromAnnotations(new PsiAnnotation[]{annotation});
    NullabilityAnnotationInfo info = typeNullability.toNullabilityAnnotationInfo();
    if (info == null) return null;
    return new NullabilityAnnotationWrapper(info);
  }

  /// Evaluates whether the wrapped annotation is redundant within the scope of a container annotation
  /// and returns that container nullability annotation info. Otherwise, returns null.
  public @Nullable NullabilityAnnotationInfo findContainerInfoForRedundantAnnotation() {
    PsiModifierListOwner listOwner = listOwner();
    if (listOwner != null && targetType() != null) {
      NullabilityAnnotationInfo info = manager().findContainerAnnotation(listOwner);
      return isRedundantInContainerScope(info) ? info : null;
    }
    else {
      PsiType type = type();
      if (type != null) {
        PsiElement context = type instanceof PsiClassType classType ? classType.getPsiContext() : info.getAnnotation();
        if (context != null) {
          NullabilityAnnotationInfo info = manager().findDefaultTypeUseNullability(context);
          return isRedundantInContainerScope(info) ? info : null;
        }
      }
    }
    return null;
  }

  private boolean isRedundantInContainerScope(@Nullable NullabilityAnnotationInfo containerInfo) {
    return containerInfo != null &&
           !containerInfo.getAnnotation().equals(info.getAnnotation()) &&
           containerInfo.getNullability() == info.getNullability();
  }

  /// @return modifier list owner
  public @Nullable PsiModifierListOwner listOwner() {
    return annotation().getOwner() instanceof PsiModifierList modifierList
           ? tryCast(modifierList.getParent(), PsiModifierListOwner.class)
           : null;
  }

  /// @return related type of wrapped annotation
  public @Nullable PsiType type() {
    return getRelatedType(annotation());
  }

  /// @return target type of wrapped annotation
  public @Nullable PsiType targetType() {
    PsiModifierListOwner listOwner = listOwner();
    return listOwner == null ? null : PsiUtil.getTypeByPsiElement(listOwner);
  }

  /// @return qualified name of wrapped annotation
  public @Nullable String qualifiedName() {
    return annotation().getQualifiedName();
  }

  /// @return wrapped annotation owner
  public @Nullable PsiAnnotationOwner owner() {
    return annotation().getOwner();
  }

  /// @return nullability represented by wrapped annotation
  public Nullability nullability() {
    return info.getNullability();
  }

  /// @return wrapped annotation
  public PsiAnnotation annotation() {
    return info.getAnnotation();
  }

  private NullableNotNullManager manager() {
    return NullableNotNullManager.getInstance(info.getAnnotation().getProject());
  }
}
