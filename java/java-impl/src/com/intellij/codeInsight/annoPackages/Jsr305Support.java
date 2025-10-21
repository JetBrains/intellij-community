// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.*;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class Jsr305Support implements AnnotationPackageSupport {
  public static final String JAVAX_ANNOTATION_NULLABLE = "javax.annotation.Nullable";
  public static final String JAVAX_ANNOTATION_NONNULL = "javax.annotation.Nonnull";
  public static final String JAVAX_ANNOTATION_CHECK_FOR_NULL = "javax.annotation.CheckForNull";
  public static final String TYPE_QUALIFIER_NICKNAME = "javax.annotation.meta.TypeQualifierNickname";

  @Override
  public @NotNull ContextNullabilityInfo getNullabilityByContainerAnnotation(@NotNull PsiAnnotation annotation,
                                                                                 PsiAnnotation.TargetType @NotNull [] placeTargetTypes,
                                                                                 boolean superPackage) {
    if (superPackage) return ContextNullabilityInfo.EMPTY;
    PsiClass declaration = annotation.resolveAnnotationType();
    PsiModifierList modList = declaration == null ? null : declaration.getModifierList();
    if (modList == null) return ContextNullabilityInfo.EMPTY;

    PsiAnnotation tqDefault = null;
    final PsiModifierList list = declaration.getModifierList();
    if (list != null) {
      tqDefault = list.findAnnotation("javax.annotation.meta.TypeQualifierDefault");
    }
    if (tqDefault == null) return ContextNullabilityInfo.EMPTY;

    Set<PsiAnnotation.TargetType> required = AnnotationTargetUtil.extractRequiredAnnotationTargets(tqDefault.findAttributeValue(null));
    if (required == null || required.isEmpty()) return ContextNullabilityInfo.EMPTY;
    boolean targetApplies = ArrayUtil.contains(PsiAnnotation.TargetType.LOCAL_VARIABLE, placeTargetTypes) ?
                            required.contains(PsiAnnotation.TargetType.LOCAL_VARIABLE) :
                            ContainerUtil.intersects(required, Arrays.asList(placeTargetTypes));
    if (!targetApplies) return ContextNullabilityInfo.EMPTY;

    for (PsiAnnotation qualifier : modList.getAnnotations()) {
      Nullability nullability = getJsr305QualifierNullability(qualifier);
      if (nullability != null) {
        return ContextNullabilityInfo.constant(new NullabilityAnnotationInfo(annotation, nullability, true));
      }
    }
    return ContextNullabilityInfo.EMPTY;
  }

  private static @Nullable Nullability getJsr305QualifierNullability(@NotNull PsiAnnotation qualifier) {
    String qName = qualifier.getQualifiedName();
    if (qName == null) return null;
    NullableNotNullManager manager = NullableNotNullManager.getInstance(qualifier.getProject());
    if (qName.equals(JAVAX_ANNOTATION_NULLABLE) && manager.getNullables().contains(qName)) {
      return Nullability.NULLABLE;
    }
    if (qName.equals(JAVAX_ANNOTATION_NONNULL)) return extractNullityFromWhenValue(qualifier);
    return manager.getAnnotationNullability(qName).orElse(null);
  }

  public static boolean isNullabilityNickName(@NotNull PsiClass candidate) {
    String qname = candidate.getQualifiedName();
    if (qname == null || qname.startsWith("javax.annotation.")) return false;
    return getNickNamedNullability(candidate) != null;
  }

  /**
   * @param psiClass annotation class
   * @return nicknamed nullability declared by this annotation; null if this annotation is not a nullability nickname annotation
   */
  public static @Nullable Nullability getNickNamedNullability(@NotNull PsiClass psiClass) {
    PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList == null) return null;
    if (!modifierList.hasAnnotation(TYPE_QUALIFIER_NICKNAME)) return null;

    PsiAnnotation nonNull = modifierList.findAnnotation(JAVAX_ANNOTATION_NONNULL);
    if (nonNull != null) {
      return extractNullityFromWhenValue(nonNull);
    }
    if (modifierList.hasAnnotation(JAVAX_ANNOTATION_CHECK_FOR_NULL)) {
      return Nullability.NULLABLE;
    }
    return null;
  }

  public static @Nullable Nullability extractNullityFromWhenValue(@NotNull PsiAnnotation nonNull) {
    PsiAnnotationMemberValue when = nonNull.findAttributeValue("when");
    if (when instanceof PsiReferenceExpression) {
      String refName = ((PsiReferenceExpression)when).getReferenceName();
      if ("ALWAYS".equals(refName)) {
        return Nullability.NOT_NULL;
      }
      if ("MAYBE".equals(refName) || "NEVER".equals(refName)) {
        return Nullability.NULLABLE;
      }
      if ("UNKNOWN".equals(refName)) {
        return Nullability.UNKNOWN;
      }
    }

    // 'when' is unknown and annotation is known -> default value (for javax.annotation.Nonnull is ALWAYS)
    if (when == null) {
      return Nullability.NOT_NULL;
    }
    return null;
  }

  @Override
  public @NotNull List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> Collections.singletonList(JAVAX_ANNOTATION_NONNULL);
      case NULLABLE -> Arrays.asList(JAVAX_ANNOTATION_NULLABLE, JAVAX_ANNOTATION_CHECK_FOR_NULL);
      case UNKNOWN -> Collections.emptyList();
    };
  }
}
