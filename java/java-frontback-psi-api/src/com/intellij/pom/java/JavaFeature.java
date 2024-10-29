// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.java;

import com.intellij.core.JavaPsiBundle;
import org.jetbrains.annotations.*;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Represents Java language, JVM, or standard library features and provides information 
 * whether a particular features is available in a given context
 */
public enum JavaFeature {
  ASSERTIONS(LanguageLevel.JDK_1_4, "feature.assertions"),
  ENUMS(LanguageLevel.JDK_1_5, "feature.enums"),
  GENERICS(LanguageLevel.JDK_1_5, "feature.generics"),
  ANNOTATIONS(LanguageLevel.JDK_1_5, "feature.annotations"),
  STATIC_IMPORTS(LanguageLevel.JDK_1_5, "feature.static.imports"),
  FOR_EACH(LanguageLevel.JDK_1_5, "feature.for.each"),
  VARARGS(LanguageLevel.JDK_1_5, "feature.varargs"),
  OVERRIDE_INTERFACE(LanguageLevel.JDK_1_6, "feature.override.interface"),
  HEX_FP_LITERALS(LanguageLevel.JDK_1_5, "feature.hex.fp.literals"),
  DIAMOND_TYPES(LanguageLevel.JDK_1_7, "feature.diamond.types"),
  MULTI_CATCH(LanguageLevel.JDK_1_7, "feature.multi.catch", true),
  TRY_WITH_RESOURCES(LanguageLevel.JDK_1_7, "feature.try.with.resources"),
  BIN_LITERALS(LanguageLevel.JDK_1_7, "feature.binary.literals"),
  UNDERSCORES(LanguageLevel.JDK_1_7, "feature.underscores.in.literals"),
  STRING_SWITCH(LanguageLevel.JDK_1_7, "feature.string.switch"),
  OBJECTS_CLASS(LanguageLevel.JDK_1_7, "feature.objects.class"),
  STREAM_OPTIONAL(LanguageLevel.JDK_1_8, "feature.stream.and.optional.api", true),
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
  EFFECTIVELY_FINAL(LanguageLevel.JDK_1_8, "feature.effectively.final"),
  REFS_AS_RESOURCE(LanguageLevel.JDK_1_9, "feature.try.with.resources.refs"),
  MODULES(LanguageLevel.JDK_1_9, "feature.modules"),
  COLLECTION_FACTORIES(LanguageLevel.JDK_1_9, "feature.collection.factories"),
  PRIVATE_INTERFACE_METHODS(LanguageLevel.JDK_1_9, "feature.private.interface.methods"),
  UTF8_PROPERTY_FILES(LanguageLevel.JDK_1_9, "feature.utf8.property.files"),
  LVTI(LanguageLevel.JDK_10, "feature.lvti"),
  VAR_LAMBDA_PARAMETER(LanguageLevel.JDK_11, "feature.var.lambda.parameter"),
  NESTMATES(LanguageLevel.JDK_11, "feature.nestmates"),
  ENHANCED_SWITCH(LanguageLevel.JDK_14, "feature.enhanced.switch"),
  SWITCH_EXPRESSION(LanguageLevel.JDK_14, "feature.switch.expressions"),
  SERIAL_ANNOTATION(LanguageLevel.JDK_14, "feature.serial.annotation"),
  TEXT_BLOCK_ESCAPES(LanguageLevel.JDK_15, "feature.text.block.escape.sequences"),
  TEXT_BLOCKS(LanguageLevel.JDK_15, "feature.text.blocks"),
  RECORDS(LanguageLevel.JDK_16, "feature.records"),
  PATTERNS(LanguageLevel.JDK_16, "feature.patterns.instanceof"),
  LOCAL_INTERFACES(LanguageLevel.JDK_16, "feature.local.interfaces"),
  LOCAL_ENUMS(LanguageLevel.JDK_16, "feature.local.enums"),
  INNER_STATICS(LanguageLevel.JDK_16, "feature.inner.statics"),
  SEALED_CLASSES(LanguageLevel.JDK_17, "feature.sealed.classes"),
  ALWAYS_STRICTFP(LanguageLevel.JDK_17, "feature.strictfp"),
  INNER_NOT_CAPTURE_THIS(LanguageLevel.JDK_18, "feature.no.this.capture"),
  JAVADOC_SNIPPETS(LanguageLevel.JDK_18, "feature.javadoc.snippets"),
  PATTERNS_IN_SWITCH(LanguageLevel.JDK_21, "feature.patterns.in.switch",
                     LanguageLevel.JDK_17_PREVIEW, LanguageLevel.JDK_18_PREVIEW, LanguageLevel.JDK_19_PREVIEW, LanguageLevel.JDK_20_PREVIEW),
  PATTERN_GUARDS_AND_RECORD_PATTERNS(LanguageLevel.JDK_21, "feature.pattern.guard.and.record.patterns",
                                     LanguageLevel.JDK_19_PREVIEW, LanguageLevel.JDK_20_PREVIEW),
  VIRTUAL_THREADS(LanguageLevel.JDK_21, "feature.virtual.threads",
                  LanguageLevel.JDK_19_PREVIEW, LanguageLevel.JDK_20_PREVIEW),
  FOREIGN_FUNCTIONS(LanguageLevel.JDK_21, "feature.foreign.functions",
                    LanguageLevel.JDK_19_PREVIEW, LanguageLevel.JDK_20_PREVIEW),
  ENUM_QUALIFIED_NAME_IN_SWITCH(LanguageLevel.JDK_21, "feature.enum.qualified.name.in.switch"),
  SEQUENCED_COLLECTIONS(LanguageLevel.JDK_21, "feature.sequenced.collections"),
  STRING_TEMPLATES(LanguageLevel.JDK_21_PREVIEW, "feature.string.templates") {
    @Override
    public boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return super.isSufficient(useSiteLevel) && !useSiteLevel.isAtLeast(LanguageLevel.JDK_23);
    }

    @Override
    public boolean isLimited() {
      return true;
    }
  },
  UNNAMED_PATTERNS_AND_VARIABLES(LanguageLevel.JDK_22, "feature.unnamed.vars") {
    @Override
    public boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
      return super.isSufficient(useSiteLevel) || LanguageLevel.JDK_21_PREVIEW == useSiteLevel;
    }
  },
  //jep 463,477
  IMPLICIT_CLASSES(LanguageLevel.JDK_21_PREVIEW, "feature.implicit.classes"),
  INSTANCE_MAIN_METHOD(LanguageLevel.JDK_21_PREVIEW, "feature.instance.main.method"),

  SCOPED_VALUES(LanguageLevel.JDK_21_PREVIEW, "feature.scoped.values"),
  STRUCTURED_CONCURRENCY(LanguageLevel.JDK_21_PREVIEW, "feature.structured.concurrency"),
  CLASSFILE_API(LanguageLevel.JDK_22_PREVIEW, "feature.classfile.api"),
  STREAM_GATHERERS(LanguageLevel.JDK_22_PREVIEW, "feature.stream.gatherers"),
  STATEMENTS_BEFORE_SUPER(LanguageLevel.JDK_22_PREVIEW, "feature.statements.before.super"),
  /**
   * Was a preview feature in Java 20 Preview. 
   * Keep the implementation, as it could reappear in the future.
   */
  RECORD_PATTERNS_IN_FOR_EACH(LanguageLevel.JDK_X, "feature.record.patterns.in.for.each",
                              LanguageLevel.JDK_20_PREVIEW),

  //jep 463,477
  INHERITED_STATIC_MAIN_METHOD(LanguageLevel.JDK_22_PREVIEW, "feature.inherited.static.main.method"),
  IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES(LanguageLevel.JDK_23_PREVIEW, "feature.implicit.import.in.implicit.classes"),
  PRIMITIVE_TYPES_IN_PATTERNS(LanguageLevel.JDK_23_PREVIEW, "feature.primitive.types.in.patterns"),

  MODULE_IMPORT_DECLARATIONS(LanguageLevel.JDK_23_PREVIEW, "feature.module.import.declarations"),
  ;

  private final @NotNull LanguageLevel myLevel;
  
  @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) 
  private final @NotNull String myKey;
  private final boolean myCanBeCustomized;
  private final Set<LanguageLevel> myObsoletePreviewLevels;

  JavaFeature(@NotNull LanguageLevel level, @NotNull @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String key) {
    this(level, key, false);
  }

  JavaFeature(@NotNull LanguageLevel level, @NotNull @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String key,
              @NotNull LanguageLevel @NotNull ... obsoletePreviewLevels) {
    myLevel = level;
    myKey = key;
    myCanBeCustomized = false;
    myObsoletePreviewLevels = EnumSet.noneOf(LanguageLevel.class);
    for (LanguageLevel obsoletePreviewLevel : obsoletePreviewLevels) {
      if (!obsoletePreviewLevel.isUnsupported()) throw new IllegalArgumentException(obsoletePreviewLevel.toString());
      myObsoletePreviewLevels.add(obsoletePreviewLevel);
    }
  }

  JavaFeature(@NotNull LanguageLevel level, @NotNull @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String key,
              boolean canBeCustomized) {
    myLevel = level;
    myKey = key;
    myCanBeCustomized = canBeCustomized;
    myObsoletePreviewLevels = Collections.emptySet();
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
   * In most of the cases, {@code PsiUtil.isAvailable(PsiElement)} or {@link #isSufficient(LanguageLevel)} should be used instead.
   */
  public @NotNull LanguageLevel getMinimumLevel() {
    return myLevel;
  }

  /**
   * @return true if the availability of this feature can be additionally filtered using {@link LanguageFeatureProvider}.
   */
  @Contract(pure = true)
  public boolean canBeCustomized() {
    return myCanBeCustomized;
  }

  @Contract(pure = true)
  public boolean isSufficient(@NotNull LanguageLevel useSiteLevel) {
    return (useSiteLevel.isAtLeast(myLevel) || 
            useSiteLevel.isUnsupported() && myObsoletePreviewLevels.contains(useSiteLevel)) &&
           (!myLevel.isPreview() || useSiteLevel.isPreview());
  }

  @Contract(pure = true)
  public boolean isLimited() {
    return false;
  }

  /**
   * Override if feature was preview and then accepted as standard
   */
  @Contract(pure = true)
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
