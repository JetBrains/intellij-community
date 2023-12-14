// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.*;

import java.util.function.Consumer;

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
  TEXT_BLOCKS(LanguageLevel.JDK_15, "feature.text.blocks"),
  SEALED_CLASSES(LanguageLevel.JDK_17, "feature.sealed.classes"),
  LOCAL_INTERFACES(LanguageLevel.JDK_16, "feature.local.interfaces"),
  LOCAL_ENUMS(LanguageLevel.JDK_16, "feature.local.enums"),
  INNER_STATICS(LanguageLevel.JDK_16, "feature.inner.statics"),
  PARENTHESIZED_PATTERNS(LanguageLevel.JDK_20_PREVIEW, "feature.parenthesised.patterns"){
    @Override
    boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      LanguageLevel until = LanguageLevel.JDK_20_PREVIEW;
      return until == useSiteLevel;
    }

    @Override
    boolean isLimited() {
      return true;
    }
  },
  PATTERNS_IN_SWITCH(LanguageLevel.JDK_21, "feature.patterns.in.switch") {
    @Override
    boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return super.isSufficient(useSiteLevel) || LanguageLevel.JDK_20_PREVIEW == useSiteLevel;
    }
  },
  PATTERN_GUARDS_AND_RECORD_PATTERNS(LanguageLevel.JDK_21, "feature.pattern.guard.and.record.patterns"){
    @Override
    boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return super.isSufficient(useSiteLevel) || LanguageLevel.JDK_20_PREVIEW == useSiteLevel;
    }
  },
  RECORD_PATTERNS_IN_FOR_EACH(LanguageLevel.JDK_20_PREVIEW, "feature.record.patterns.in.for.each"){
    @Override
    boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return LanguageLevel.JDK_20_PREVIEW == useSiteLevel.getSupportedLevel();
    }

    @Override
    boolean isLimited() {
      return true;
    }
  },
  VIRTUAL_THREADS(LanguageLevel.JDK_20_PREVIEW, "feature.virtual.threads") {
    @Override
    boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return LanguageLevel.JDK_20_PREVIEW == useSiteLevel.getSupportedLevel();
    }

    @Override
    boolean isLimited() {
      return true;
    }
  },
  FOREIGN_FUNCTIONS(LanguageLevel.JDK_20_PREVIEW, "feature.foreign.functions") {
    @Override
    boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return LanguageLevel.JDK_20_PREVIEW == useSiteLevel.getSupportedLevel();
    }

    @Override
    boolean isLimited() {
      return true;
    }
  },
  ENUM_QUALIFIED_NAME_IN_SWITCH(LanguageLevel.JDK_21, "feature.enum.qualified.name.in.switch"),
  STRING_TEMPLATES(LanguageLevel.JDK_21_PREVIEW, "feature.string.templates"),
  UNNAMED_PATTERNS_AND_VARIABLES(LanguageLevel.JDK_22, "feature.unnamed.vars") {
    @Override
    boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return super.isSufficient(useSiteLevel) || LanguageLevel.JDK_21_PREVIEW == useSiteLevel;
    }
  },
  IMPLICIT_CLASSES(LanguageLevel.JDK_21_PREVIEW, "feature.implicit.classes"),
  SCOPED_VALUES(LanguageLevel.JDK_21_PREVIEW, "feature.scoped.values"),
  STRUCTURED_CONCURRENCY(LanguageLevel.JDK_21_PREVIEW, "feature.structured.concurrency"),
  CLASSFILE_API(LanguageLevel.JDK_22_PREVIEW, "feature.classfile.api"),
  STREAM_GATHERERS(LanguageLevel.JDK_22_PREVIEW, "feature.stream.gatherers"),
  ;

  public static final @NonNls String JDK_INTERNAL_PREVIEW_FEATURE = "jdk.internal.PreviewFeature";
  public static final @NonNls String JDK_INTERNAL_JAVAC_PREVIEW_FEATURE = "jdk.internal.javac.PreviewFeature";

  final LanguageLevel level;
  @PropertyKey(resourceBundle = JavaErrorBundle.BUNDLE) final String key;

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
    useSiteLevel = useSiteLevel.getSupportedLevel();
    return useSiteLevel.isAtLeast(level) &&
           (!level.isPreview() || useSiteLevel.isPreview());
  }

  boolean isLimited() {
    return false;
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

  // Should correspond to jdk.internal.javac.PreviewFeature.Feature enum
  @Nullable
  @Contract(pure = true)
  private static HighlightingFeature convertFromPreviewFeatureName(@NotNull @NonNls String feature) {
    return switch (feature) {
      case "PATTERN_MATCHING_IN_INSTANCEOF" -> PATTERNS;
      case "TEXT_BLOCKS" -> TEXT_BLOCKS;
      case "RECORDS" -> RECORDS;
      case "SEALED_CLASSES" -> SEALED_CLASSES;
      case "STRING_TEMPLATES" -> STRING_TEMPLATES;
      case "UNNAMED_CLASSES", "IMPLICIT_CLASSES" -> IMPLICIT_CLASSES;
      case "SCOPED_VALUES" -> SCOPED_VALUES;
      case "STRUCTURED_CONCURRENCY" -> STRUCTURED_CONCURRENCY;
      case "CLASSFILE_API" -> CLASSFILE_API;
      case "STREAM_GATHERERS" -> STREAM_GATHERERS;
      case "FOREIGN" -> FOREIGN_FUNCTIONS;
      case "VIRTUAL_THREADS" -> VIRTUAL_THREADS;
      default -> null;
    };
  }

  @Nullable
  @Contract(value = "null -> null", pure = true)
  public static PsiAnnotation getPreviewFeatureAnnotation(@Nullable PsiModifierListOwner owner) {
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

  static void checkPreviewFeature(@NotNull PsiElement statement, @Nullable HighlightingFeature.PreviewFeatureVisitor visitor) {
    if (visitor != null) {
      statement.accept(visitor);
    }
  }

  static class PreviewFeatureVisitor extends PreviewFeatureVisitorBase {
    private final LanguageLevel myLanguageLevel;
    private final Consumer<? super HighlightInfo.Builder> myErrorSink;

    PreviewFeatureVisitor(@NotNull LanguageLevel languageLevel, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
      myLanguageLevel = languageLevel;
      myErrorSink = errorSink;
    }

    @Override
    protected void registerProblem(PsiElement element, String description, HighlightingFeature feature, PsiAnnotation annotation) {
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
