// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.*;

import static com.intellij.util.ObjectUtils.tryCast;

public enum HighlightingFeature {
  GENERICS(LanguageLevel.JDK_1_5, "feature.generics"),
  ANNOTATIONS(LanguageLevel.JDK_1_5, "feature.annotations"),
  STATIC_IMPORTS(LanguageLevel.JDK_1_5, "feature.static.imports"),
  FOR_EACH(LanguageLevel.JDK_1_5, "feature.for.each"),
  VARARGS(LanguageLevel.JDK_1_5, "feature.varargs"),
  HEX_FP_LITERALS(LanguageLevel.JDK_1_5, "feature.hex.fp.literals"),
  DIAMOND_TYPES(LanguageLevel.JDK_1_7, "feature.diamond.types"),
  MULTI_CATCH(LanguageLevel.JDK_1_7, "feature.multi.catch"),
  TRY_WITH_RESOURCES(LanguageLevel.JDK_1_7, "feature.try.with.resources"),
  BIN_LITERALS(LanguageLevel.JDK_1_7, "feature.binary.literals"),
  UNDERSCORES(LanguageLevel.JDK_1_7, "feature.underscores.in.literals"),
  EXTENSION_METHODS(LanguageLevel.JDK_1_8, "feature.extension.methods"),
  METHOD_REFERENCES(LanguageLevel.JDK_1_8, "feature.method.references"),
  LAMBDA_EXPRESSIONS(LanguageLevel.JDK_1_8, "feature.lambda.expressions"),
  TYPE_ANNOTATIONS(LanguageLevel.JDK_1_8, "feature.type.annotations"),
  RECEIVERS(LanguageLevel.JDK_1_8, "feature.type.receivers"),
  INTERSECTION_CASTS(LanguageLevel.JDK_1_8, "feature.intersections.in.casts"),
  STATIC_INTERFACE_CALLS(LanguageLevel.JDK_1_8, "feature.static.interface.calls"),
  REFS_AS_RESOURCE(LanguageLevel.JDK_1_9, "feature.try.with.resources.refs"),
  MODULES(LanguageLevel.JDK_1_9, "feature.modules"),
  LVTI(LanguageLevel.JDK_10, "feature.lvti"),
  VAR_LAMBDA_PARAMETER(LanguageLevel.JDK_11, "feature.var.lambda.parameter"),
  ENHANCED_SWITCH(LanguageLevel.JDK_14, "feature.enhanced.switch"),
  SWITCH_EXPRESSION(LanguageLevel.JDK_14, "feature.switch.expressions"),
  RECORDS(LanguageLevel.JDK_16, "feature.records"),
  PATTERNS(LanguageLevel.JDK_16, "feature.patterns.instanceof"),
  TEXT_BLOCK_ESCAPES(LanguageLevel.JDK_15, "feature.text.block.escape.sequences"),
  TEXT_BLOCKS(LanguageLevel.JDK_15, "feature.text.blocks") ,
  SEALED_CLASSES(LanguageLevel.JDK_16_PREVIEW, "feature.sealed.classes") {
    @Override
    boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return useSiteLevel == LanguageLevel.JDK_16_PREVIEW ||
             useSiteLevel.isAtLeast(LanguageLevel.JDK_17);
    }

    @Override
    LanguageLevel getStandardLevel() {
      return LanguageLevel.JDK_17;
    }
  },
  LOCAL_INTERFACES(LanguageLevel.JDK_16, "feature.local.interfaces"),
  LOCAL_ENUMS(LanguageLevel.JDK_16, "feature.local.enums"),
  INNER_STATICS(LanguageLevel.JDK_16, "feature.inner.statics"),
  PATTERNS_IN_SWITCH(LanguageLevel.JDK_17_PREVIEW, "feature.patterns.in.switch"),
  GUARDED_AND_PARENTHESIZED_PATTERNS(LanguageLevel.JDK_17_PREVIEW, "feature.guarded.and.parenthesised.patterns");

  public static final @NonNls String JDK_INTERNAL_PREVIEW_FEATURE = "jdk.internal.PreviewFeature";
  public static final @NonNls String JDK_INTERNAL_JAVAC_PREVIEW_FEATURE = "jdk.internal.javac.PreviewFeature";

  final LanguageLevel level;
  @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE)
  final String key;

  HighlightingFeature(@NotNull LanguageLevel level, @NotNull @PropertyKey(resourceBundle = JavaAnalysisBundle.BUNDLE) String key) {
    this.level = level;
    this.key = key;
  }

  public LanguageLevel getLevel() {
    return level;
  }

  /**
   * @param element a valid PsiElement to check (it's better to supply PsiFile if already known; any element is accepted for convenience)
   * @return true if this feature is available in the PsiFile the supplied element belongs to
   */
  public boolean isAvailable(@NotNull PsiElement element) {
    return isSufficient(PsiUtil.getLanguageLevel(element));
  }

  boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
    return useSiteLevel.isAtLeast(level) && (!level.isPreview() || useSiteLevel.isPreview());
  }

  /**
   * Override if feature was preview and then accepted as standard
   */
  LanguageLevel getStandardLevel() {
    return level.isPreview() ? null : level;
  }

  @Nullable
  @Contract(value = "null -> null", pure = true)
  public static HighlightingFeature fromPreviewFeatureAnnotation(@Nullable PsiAnnotation annotation) {
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

    return convertFromPreviewFeatureName(enumConstant.getName());
  }

  @Nullable
  @Contract(pure = true)
  private static HighlightingFeature convertFromPreviewFeatureName(@NotNull @NonNls String feature) {
    switch (feature) {
      case "PATTERN_MATCHING_IN_INSTANCEOF":
        return PATTERNS;
      case "TEXT_BLOCKS":
        return TEXT_BLOCKS;
      case "RECORDS":
        return RECORDS;
      case "SEALED_CLASSES":
        return SEALED_CLASSES;
      default:
        return null;
    }
  }

  @Nullable
  @Contract(value = "null -> null", pure = true)
  public static PsiAnnotation getPreviewFeatureAnnotation(@Nullable PsiModifierListOwner owner) {
    if (owner == null) return null;

    PsiAnnotation annotation = getAnnotation(owner);
    if (annotation != null) return annotation;

    if (owner instanceof PsiMember && !owner.hasModifier(JvmModifier.STATIC)) {
      PsiMember member = (PsiMember)owner;
      PsiAnnotation result = getPreviewFeatureAnnotation(member.getContainingClass());
      if (result != null) return result;
    }

    PsiPackage psiPackage = JavaResolveUtil.getContainingPackage(owner);
    if (psiPackage  == null) return null;

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
}
