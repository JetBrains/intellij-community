// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Utilities to support Java preview feature highlighting
 */
public final class PreviewFeatureUtil {
  /**
   * Internal JDK annotation for preview feature APIs
   */
  public static final @NonNls String JDK_INTERNAL_PREVIEW_FEATURE = "jdk.internal.PreviewFeature";
  public static final @NonNls String JDK_INTERNAL_JAVAC_PREVIEW_FEATURE = "jdk.internal.javac.PreviewFeature";

  static void checkPreviewFeature(@NotNull PsiElement element, @Nullable PreviewFeatureUtil.PreviewFeatureVisitor visitor) {
    if (visitor != null) {
      element.accept(visitor);
    }
  }

  /**
   * @param annotation annotation to get the language feature from
   * @return language feature referenced by a given annotation; null if the annotation is not preview feature annotation.
   * @see #JDK_INTERNAL_PREVIEW_FEATURE
   * @see #JDK_INTERNAL_JAVAC_PREVIEW_FEATURE
   */
  @Contract(value = "null -> null", pure = true)
  public static @Nullable JavaFeature fromPreviewFeatureAnnotation(@Nullable PsiAnnotation annotation) {
    if (annotation == null) return null;
    if (!annotation.hasQualifiedName(JDK_INTERNAL_PREVIEW_FEATURE) &&
        !annotation.hasQualifiedName(JDK_INTERNAL_JAVAC_PREVIEW_FEATURE)) {
      return null;
    }

    PsiNameValuePair feature = AnnotationUtil.findDeclaredAttribute(annotation, "feature");
    if (feature == null) return null;

    PsiReferenceExpression referenceExpression = tryCast(feature.getDetachedValue(), PsiReferenceExpression.class);
    if (referenceExpression == null) return null;

    PsiEnumConstant enumConstant = tryCast(referenceExpression.resolve(), PsiEnumConstant.class);
    if (enumConstant == null) return null;

    return JavaFeature.convertFromPreviewFeatureName(enumConstant.getName());
  }

  @Contract(value = "null -> null", pure = true)
  public static @Nullable PsiAnnotation getPreviewFeatureAnnotation(@Nullable PsiModifierListOwner owner) {
    if (owner == null) return null;

    PsiAnnotation annotation = getAnnotation(owner);
    if (annotation != null) return annotation;

    if (owner instanceof PsiMember member && !owner.hasModifier(JvmModifier.STATIC)) {
      PsiAnnotation result = getPreviewFeatureAnnotation(member.getContainingClass());
      if (result != null) return result;
    }

    PsiPackage psiPackage = JavaResolveUtil.getContainingPackage(owner);
    if (psiPackage == null) return null;

    PsiAnnotation packageAnnotation = getAnnotation(psiPackage);
    if (packageAnnotation != null) return packageAnnotation;

    PsiJavaModule module = JavaModuleGraphUtil.findDescriptorByElement(owner);
    if (module == null) return null;

    return getAnnotation(module);
  }

  private static PsiAnnotation getAnnotation(@NotNull PsiModifierListOwner owner) {
    PsiAnnotation annotation = owner.getAnnotation(JDK_INTERNAL_JAVAC_PREVIEW_FEATURE);
    if (annotation != null) return annotation;

    return owner.getAnnotation(JDK_INTERNAL_PREVIEW_FEATURE);
  }

  static class PreviewFeatureVisitor extends PreviewFeatureVisitorBase {
    private final LanguageLevel myLanguageLevel;
    private final Consumer<? super HighlightInfo.Builder> myErrorSink;

    PreviewFeatureVisitor(@NotNull LanguageLevel languageLevel, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
      myLanguageLevel = languageLevel;
      myErrorSink = errorSink;
    }

    @Override
    protected void registerProblem(PsiElement element, String description, JavaFeature feature, PsiAnnotation annotation) {
      boolean isReflective = Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(annotation, "reflective"));

      HighlightInfoType type = isReflective ? HighlightInfoType.WARNING : HighlightInfoType.ERROR;

      HighlightInfo.Builder highlightInfo =
        HighlightUtil.checkFeature(element, feature, myLanguageLevel, element.getContainingFile(), description, type);
      if (highlightInfo != null) {
        myErrorSink.accept(highlightInfo);
      }
    }
  }
}
