// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.java;

import com.intellij.core.JavaPsiBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.*;

/**
 * Represents Java language and standard library features and provides information 
 * whether a particular features is available in a given context
 */
public enum JavaFeature {
  GENERICS(LanguageLevel.JDK_1_5, "feature.generics"),
  ANNOTATIONS(LanguageLevel.JDK_1_5, "feature.annotations"),
  STATIC_IMPORTS(LanguageLevel.JDK_1_5, "feature.static.imports"),
  FOR_EACH(LanguageLevel.JDK_1_5, "feature.for.each"),
  VARARGS(LanguageLevel.JDK_1_5, "feature.varargs"),
  HEX_FP_LITERALS(LanguageLevel.JDK_1_5, "feature.hex.fp.literals"),
  DIAMOND_TYPES(LanguageLevel.JDK_1_7, "feature.diamond.types"),
  MULTI_CATCH(LanguageLevel.JDK_1_7, "feature.multi.catch", true),
  TRY_WITH_RESOURCES(LanguageLevel.JDK_1_7, "feature.try.with.resources"),
  BIN_LITERALS(LanguageLevel.JDK_1_7, "feature.binary.literals"),
  UNDERSCORES(LanguageLevel.JDK_1_7, "feature.underscores.in.literals"),
  STREAMS(LanguageLevel.JDK_1_8, "feature.stream.api", true),
  /**
   * java.util.Arrays.setAll, java.util.Collection#removeIf, java.util.List.sort(Comparator),
   * java.util.Map#putIfAbsent, java.util.Map#forEach
   */
  ADVANCED_COLLECTIONS_API(LanguageLevel.JDK_1_8, "feature.advanced.collection.api", true),
  /**
   * ThreadLocal.withInitial
   */
  THREAD_LOCAL_WITH_INITIAL(LanguageLevel.JDK_1_8, "feature.with.initial", true),
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
    public boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      LanguageLevel until = LanguageLevel.JDK_20_PREVIEW;
      return until == useSiteLevel;
    }

    @Override
    public boolean isLimited() {
      return true;
    }
  },
  PATTERNS_IN_SWITCH(LanguageLevel.JDK_21, "feature.patterns.in.switch") {
    @Override
    public boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return super.isSufficient(useSiteLevel) || LanguageLevel.JDK_20_PREVIEW == useSiteLevel;
    }
  },
  PATTERN_GUARDS_AND_RECORD_PATTERNS(LanguageLevel.JDK_21, "feature.pattern.guard.and.record.patterns"){
    @Override
    public boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return super.isSufficient(useSiteLevel) || LanguageLevel.JDK_20_PREVIEW == useSiteLevel;
    }
  },
  RECORD_PATTERNS_IN_FOR_EACH(LanguageLevel.JDK_20_PREVIEW, "feature.record.patterns.in.for.each"){
    @Override
    public boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return LanguageLevel.JDK_20_PREVIEW == useSiteLevel.getSupportedLevel();
    }

    @Override
    public boolean isLimited() {
      return true;
    }
  },
  VIRTUAL_THREADS(LanguageLevel.JDK_20_PREVIEW, "feature.virtual.threads") {
    @Override
    public boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return LanguageLevel.JDK_20_PREVIEW == useSiteLevel.getSupportedLevel();
    }

    @Override
    public boolean isLimited() {
      return true;
    }
  },
  FOREIGN_FUNCTIONS(LanguageLevel.JDK_20_PREVIEW, "feature.foreign.functions") {
    @Override
    public boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return LanguageLevel.JDK_20_PREVIEW == useSiteLevel.getSupportedLevel();
    }

    @Override
    public boolean isLimited() {
      return true;
    }
  },
  ENUM_QUALIFIED_NAME_IN_SWITCH(LanguageLevel.JDK_21, "feature.enum.qualified.name.in.switch"),
  STRING_TEMPLATES(LanguageLevel.JDK_21_PREVIEW, "feature.string.templates"),
  UNNAMED_PATTERNS_AND_VARIABLES(LanguageLevel.JDK_22, "feature.unnamed.vars") {
    @Override
    public boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return super.isSufficient(useSiteLevel) || LanguageLevel.JDK_21_PREVIEW == useSiteLevel;
    }
  },
  IMPLICIT_CLASSES(LanguageLevel.JDK_21_PREVIEW, "feature.implicit.classes"),
  SCOPED_VALUES(LanguageLevel.JDK_21_PREVIEW, "feature.scoped.values"),
  STRUCTURED_CONCURRENCY(LanguageLevel.JDK_21_PREVIEW, "feature.structured.concurrency"),
  CLASSFILE_API(LanguageLevel.JDK_22_PREVIEW, "feature.classfile.api"),
  STREAM_GATHERERS(LanguageLevel.JDK_22_PREVIEW, "feature.stream.gatherers"),
  STATEMENTS_BEFORE_SUPER(LanguageLevel.JDK_22_PREVIEW, "feature.statements.before.super"),
  ;

  private final @NotNull LanguageLevel myLevel;
  
  @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) 
  private final @NotNull String myKey;
  private final boolean myCanBeCustomized;

  JavaFeature(@NotNull LanguageLevel level, @NotNull @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String key) {
    this(level, key, false);
  }

  JavaFeature(@NotNull LanguageLevel level, @NotNull @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String key,
              boolean canBeCustomized) {
    myLevel = level;
    myKey = key;
    myCanBeCustomized = canBeCustomized;
  }

  /**
   * @return Human-readable feature name
   */
  public @NotNull @Nls String getFeatureName() {
    return JavaPsiBundle.message(myKey);
  }

  /**
   * @return minimal language level where feature is available.
   * Note that this doesn't mean that the feature is available on every language level which is higher.
   * In most of the cases, {@link #isAvailable(PsiElement)} or {@link #isSufficient(LanguageLevel)} should be used instead.
   */
  public @NotNull LanguageLevel getMinimumLevel() {
    return myLevel;
  }

  /**
   * @param element a valid PsiElement to check (it's better to supply PsiFile if already known; any element is accepted for convenience)
   * @return true if this feature is available in the PsiFile the supplied element belongs to
   */
  public boolean isAvailable(@NotNull PsiElement element) {
    if (!isSufficient(PsiUtil.getLanguageLevel(element))) return false;
    if (!myCanBeCustomized) return true;
    PsiFile file = element.getContainingFile();
    if (file == null) return true;
    for (LanguageFeatureProvider extension : LanguageFeatureProvider.EXTENSION_POINT_NAME.getExtensionList()) {
      ThreeState threeState = extension.isFeatureSupported(this, file);
      if (threeState != ThreeState.UNSURE)
        return threeState.toBoolean();
    }
    return true;
  }

  public boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
    useSiteLevel = useSiteLevel.getSupportedLevel();
    return useSiteLevel.isAtLeast(myLevel) &&
           (!myLevel.isPreview() || useSiteLevel.isPreview());
  }

  public boolean isLimited() {
    return false;
  }

  /**
   * Override if feature was preview and then accepted as standard
   */
  public LanguageLevel getStandardLevel() {
    return myLevel.isPreview() ? null : myLevel;
  }

  // Should correspond to jdk.internal.javac.PreviewFeature.Feature enum
  @Nullable
  @Contract(pure = true)
  public static JavaFeature convertFromPreviewFeatureName(@NotNull @NonNls String feature) {
    switch (feature) {
      case "PATTERN_MATCHING_IN_INSTANCEOF":
        return PATTERNS;
      case "TEXT_BLOCKS":
        return TEXT_BLOCKS;
      case "RECORDS":
        return RECORDS;
      case "SEALED_CLASSES":
        return SEALED_CLASSES;
      case "STRING_TEMPLATES":
        return STRING_TEMPLATES;
      case "UNNAMED_CLASSES":
      case "IMPLICIT_CLASSES":
        return IMPLICIT_CLASSES;
      case "SCOPED_VALUES":
        return SCOPED_VALUES;
      case "STRUCTURED_CONCURRENCY":
        return STRUCTURED_CONCURRENCY;
      case "CLASSFILE_API":
        return CLASSFILE_API;
      case "STREAM_GATHERERS":
        return STREAM_GATHERERS;
      case "FOREIGN":
        return FOREIGN_FUNCTIONS;
      case "VIRTUAL_THREADS":
        return VIRTUAL_THREADS;
      default:
        return null;
    }
  }
}
