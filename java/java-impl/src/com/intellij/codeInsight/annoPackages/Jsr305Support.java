// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.annoPackages;

import com.intellij.codeInsight.*;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class Jsr305Support implements AnnotationPackageSupport {
  public static final String JAVAX_ANNOTATION_NULLABLE = "javax.annotation.Nullable";
  public static final String JAVAX_ANNOTATION_NONNULL = "javax.annotation.Nonnull";
  public static final String TYPE_QUALIFIER_NICKNAME = "javax.annotation.meta.TypeQualifierNickname";
  private final NullableNotNullManager myManager;

  Jsr305Support(NullableNotNullManager manager) {
    myManager = manager;
  }

  @Nullable
  @Override
  public NullabilityAnnotationInfo getNullabilityByContainerAnnotation(@NotNull PsiAnnotation annotation,
                                                                       PsiAnnotation.TargetType @NotNull [] placeTargetTypes,
                                                                       boolean superPackage) {
    if (superPackage) return null;
    PsiClass declaration = annotation.resolveAnnotationType();
    PsiModifierList modList = declaration == null ? null : declaration.getModifierList();
    if (modList == null) return null;

    PsiAnnotation tqDefault = AnnotationUtil.findAnnotation(declaration, true, "javax.annotation.meta.TypeQualifierDefault");
    if (tqDefault == null) return null;

    Set<PsiAnnotation.TargetType> required = AnnotationTargetUtil.extractRequiredAnnotationTargets(tqDefault.findAttributeValue(null));
    if (required == null || (!required.isEmpty() && !ContainerUtil.intersects(required, Arrays.asList(placeTargetTypes)))) return null;

    for (PsiAnnotation qualifier : modList.getAnnotations()) {
      Nullability nullability = getJsr305QualifierNullability(qualifier);
      if (nullability != null) {
        return new NullabilityAnnotationInfo(annotation, nullability, true);
      }
    }
    return null;
  }

  @Nullable
  private Nullability getJsr305QualifierNullability(@NotNull PsiAnnotation qualifier) {
    String qName = qualifier.getQualifiedName();
    if (qName == null || !qName.startsWith("javax.annotation.")) return null;

    if (qName.equals(JAVAX_ANNOTATION_NULLABLE) && myManager.getNullables().contains(qName)) return Nullability.NULLABLE;
    if (qName.equals(JAVAX_ANNOTATION_NONNULL)) return extractNullityFromWhenValue(qualifier);
    return null;
  }

  public static boolean isNullabilityNickName(@NotNull PsiClass candidate) {
    String qname = candidate.getQualifiedName();
    if (qname == null || qname.startsWith("javax.annotation.")) return false;
    return getNickNamedNullability(candidate) != Nullability.UNKNOWN;
  }

  @NotNull
  public static Nullability getNickNamedNullability(@NotNull PsiClass psiClass) {
    if (AnnotationUtil.findAnnotation(psiClass, TYPE_QUALIFIER_NICKNAME) == null) return Nullability.UNKNOWN;

    PsiAnnotation nonNull = AnnotationUtil.findAnnotation(psiClass, JAVAX_ANNOTATION_NONNULL);
    return nonNull != null ? extractNullityFromWhenValue(nonNull) : Nullability.UNKNOWN;
  }

  @NotNull
  private static Nullability extractNullityFromWhenValue(@NotNull PsiAnnotation nonNull) {
    PsiAnnotationMemberValue when = nonNull.findAttributeValue("when");
    if (when instanceof PsiReferenceExpression) {
      String refName = ((PsiReferenceExpression)when).getReferenceName();
      if ("ALWAYS".equals(refName)) {
        return Nullability.NOT_NULL;
      }
      if ("MAYBE".equals(refName) || "NEVER".equals(refName)) {
        return Nullability.NULLABLE;
      }
    }

    // 'when' is unknown and annotation is known -> default value (for javax.annotation.Nonnull is ALWAYS)
    if (when == null && JAVAX_ANNOTATION_NONNULL.equals(nonNull.getQualifiedName())) {
      return Nullability.NOT_NULL;
    }
    return Nullability.UNKNOWN;
  }

  @NotNull
  @Override
  public List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    switch (nullability) {
      case NOT_NULL:
        return Collections.singletonList(JAVAX_ANNOTATION_NONNULL);
      case NULLABLE:
        return Arrays.asList(JAVAX_ANNOTATION_NULLABLE, "javax.annotation.CheckForNull");
      default:
        return Collections.emptyList();
    }
  }
}
