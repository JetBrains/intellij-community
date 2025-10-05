// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.java

import com.intellij.java.syntax.JavaSyntaxBundle
import com.intellij.java.syntax.JavaSyntaxBundle.message
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import kotlin.jvm.JvmStatic

/**
 * Represents Java language, JVM, or standard library features and provides information
 * whether a particular features is available in a given context
 */
enum class JavaFeature {
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
  REPEATING_ANNOTATIONS(LanguageLevel.JDK_1_8, "feature.repeating.annotations"),
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
  AUTO_ROOT_MODULES(LanguageLevel.JDK_11, "feature.auto.root.modules"),
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
    override fun isSufficient(useSiteLevel: LanguageLevel): Boolean {
      return super.isSufficient(useSiteLevel) && !useSiteLevel.isAtLeast(LanguageLevel.JDK_23)
    }

    override val isLimited: Boolean get() = true
  },
  UNNAMED_PATTERNS_AND_VARIABLES(LanguageLevel.JDK_22, "feature.unnamed.vars") {
    override fun isSufficient(useSiteLevel: LanguageLevel): Boolean {
      return super.isSufficient(useSiteLevel) || LanguageLevel.JDK_21_PREVIEW == useSiteLevel
    }
  },

  /**
   * JEP 512
   * @see INSTANCE_MAIN_METHOD
   * @see IMPLICIT_CLASS_NAME_OUT_OF_SCOPE
   * @see INHERITED_STATIC_MAIN_METHOD
   * @see IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES
   * @see JAVA_LANG_IO
   */
  IMPLICIT_CLASSES(LanguageLevel.JDK_21_PREVIEW, "feature.implicit.classes") {
    override fun isSufficient(useSiteLevel: LanguageLevel): Boolean {
      return useSiteLevel.isAtLeast(LanguageLevel.JDK_25) ||
             LanguageLevel.JDK_21_PREVIEW == useSiteLevel || //jep 445
             LanguageLevel.JDK_22_PREVIEW == useSiteLevel || // jep 463
             LanguageLevel.JDK_23_PREVIEW == useSiteLevel || // jep 477
             LanguageLevel.JDK_24_PREVIEW == useSiteLevel // jep 495
    }

    override val standardLevel: LanguageLevel = LanguageLevel.JDK_25
  },

  /**
   * JEP 512
   * @see IMPLICIT_CLASSES
   * @see IMPLICIT_CLASS_NAME_OUT_OF_SCOPE
   * @see INHERITED_STATIC_MAIN_METHOD
   * @see IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES
   * @see JAVA_LANG_IO
   */
  INSTANCE_MAIN_METHOD(LanguageLevel.JDK_21_PREVIEW, "feature.instance.main.method") {
    override fun isSufficient(useSiteLevel: LanguageLevel): Boolean {
      return useSiteLevel.isAtLeast(LanguageLevel.JDK_25) ||
             LanguageLevel.JDK_21_PREVIEW == useSiteLevel || //jep 445
             LanguageLevel.JDK_22_PREVIEW == useSiteLevel || // jep 463
             LanguageLevel.JDK_23_PREVIEW == useSiteLevel || // jep 477
             LanguageLevel.JDK_24_PREVIEW == useSiteLevel // jep 495
    }

    override val standardLevel: LanguageLevel = LanguageLevel.JDK_25
  },

  SCOPED_VALUES(LanguageLevel.JDK_21_PREVIEW, "feature.scoped.values"),
  STABLE_VALUES(LanguageLevel.JDK_25_PREVIEW, "feature.stable.values"),
  STRUCTURED_CONCURRENCY(LanguageLevel.JDK_21_PREVIEW, "feature.structured.concurrency"),
  STRUCTURED_CONCURRENCY_TASK_SCOPE_CONSTRUCTORS(LanguageLevel.JDK_21_PREVIEW, "feature.structured.concurrency.constructors") {
    override fun isSufficient(useSiteLevel: LanguageLevel): Boolean {
      return super.isSufficient(useSiteLevel) && !useSiteLevel.isAtLeast(LanguageLevel.JDK_25)
    }
  },

  STRUCTURED_CONCURRENCY_TASK_SCOPE_STATIC_FACTORY_METHODS(LanguageLevel.JDK_25_PREVIEW, "feature.structured.concurrency.static.factory.methods"),

  PEM_API(LanguageLevel.JDK_25_PREVIEW, "feature.pem.api"),

  /**
   * JEP 512
   * @see IMPLICIT_CLASSES
   * @see INSTANCE_MAIN_METHOD
   * @see INHERITED_STATIC_MAIN_METHOD
   * @see IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES
   * @see JAVA_LANG_IO
   */
  IMPLICIT_CLASS_NAME_OUT_OF_SCOPE(LanguageLevel.JDK_25, "feature.implicit.class.name.out.of.scope") {
    override fun isSufficient(useSiteLevel: LanguageLevel): Boolean {
      return useSiteLevel.isAtLeast(LanguageLevel.JDK_25) ||
             LanguageLevel.JDK_22_PREVIEW == useSiteLevel || // jep 463
             LanguageLevel.JDK_23_PREVIEW == useSiteLevel || // jep 477
             LanguageLevel.JDK_24_PREVIEW == useSiteLevel // jep 495
    }

    override val standardLevel: LanguageLevel = LanguageLevel.JDK_25
  },

  CLASSFILE_API(LanguageLevel.JDK_22_PREVIEW, "feature.classfile.api"),
  STREAM_GATHERERS(LanguageLevel.JDK_22_PREVIEW, "feature.stream.gatherers"),
  STATEMENTS_BEFORE_SUPER(LanguageLevel.JDK_22_PREVIEW, "feature.statements.before.super") {
    override fun isSufficient(useSiteLevel: LanguageLevel): Boolean {
      return super.isSufficient(useSiteLevel) || useSiteLevel.isAtLeast(LanguageLevel.JDK_25)
    }

    override val standardLevel: LanguageLevel = LanguageLevel.JDK_25
  },

  /**
   * Was a preview feature in Java 20 Preview.
   * Keep the implementation, as it could reappear in the future.
   */
  RECORD_PATTERNS_IN_FOR_EACH(LanguageLevel.JDK_X, "feature.record.patterns.in.for.each",
                              LanguageLevel.JDK_20_PREVIEW),


  /**
   * JEP 512
   * @see IMPLICIT_CLASSES
   * @see INSTANCE_MAIN_METHOD
   * @see IMPLICIT_CLASS_NAME_OUT_OF_SCOPE
   * @see IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES
   * @see JAVA_LANG_IO
   */
  INHERITED_STATIC_MAIN_METHOD(LanguageLevel.JDK_22_PREVIEW, "feature.inherited.static.main.method") {
    override fun isSufficient(useSiteLevel: LanguageLevel): Boolean {
      return useSiteLevel.isAtLeast(LanguageLevel.JDK_25) ||
             LanguageLevel.JDK_22_PREVIEW == useSiteLevel || // jep 463
             LanguageLevel.JDK_23_PREVIEW == useSiteLevel || // jep 477
             LanguageLevel.JDK_24_PREVIEW == useSiteLevel // jep 495
    }

    override val standardLevel: LanguageLevel = LanguageLevel.JDK_25
  },

  /**
   * JEP 512
   * @see IMPLICIT_CLASSES
   * @see INSTANCE_MAIN_METHOD
   * @see IMPLICIT_CLASS_NAME_OUT_OF_SCOPE
   * @see INHERITED_STATIC_MAIN_METHOD
   * @see JAVA_LANG_IO
   */
  IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES(LanguageLevel.JDK_23_PREVIEW, "feature.implicit.import.in.implicit.classes") {
    override fun isSufficient(useSiteLevel: LanguageLevel): Boolean {
      return useSiteLevel.isAtLeast(LanguageLevel.JDK_25) ||
             LanguageLevel.JDK_23_PREVIEW == useSiteLevel || // jep 477
             LanguageLevel.JDK_24_PREVIEW == useSiteLevel // jep 495
    }

    override val standardLevel: LanguageLevel = LanguageLevel.JDK_25
  },

  //JEP 507
  PRIMITIVE_TYPES_IN_PATTERNS(LanguageLevel.JDK_23_PREVIEW, "feature.primitive.types.in.patterns"),

  /**
   * JEP 511
   * @see PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS
   * @see TRANSITIVE_DEPENDENCY_ON_JAVA_BASE
   */
  MODULE_IMPORT_DECLARATIONS(LanguageLevel.JDK_23_PREVIEW, "feature.module.import.declarations") {  //
    override fun isSufficient(useSiteLevel: LanguageLevel): Boolean {
      return useSiteLevel.isAtLeast(LanguageLevel.JDK_25) ||
             LanguageLevel.JDK_24_PREVIEW == useSiteLevel || //jep 494
             LanguageLevel.JDK_23_PREVIEW == useSiteLevel //jep 776
    }

    override val standardLevel: LanguageLevel = LanguageLevel.JDK_25
  },

  /**
   * Usually, this type of comments is shown as Javadoc despite language level.
   * This option can be used only to adjust behavior for cases with conflicts between different types of comments (markdown and old-style)
   */
  MARKDOWN_COMMENT(LanguageLevel.JDK_23, "feature.markdown.comment"),

  /**
   * JEP 511
   * @see MODULE_IMPORT_DECLARATIONS
   * @see TRANSITIVE_DEPENDENCY_ON_JAVA_BASE
   */
  PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS(LanguageLevel.JDK_24_PREVIEW, "feature.package.import.shadow.module.import") {
    override fun isSufficient(useSiteLevel: LanguageLevel): Boolean {
      return super.isSufficient(useSiteLevel) ||
             useSiteLevel.isAtLeast(LanguageLevel.JDK_25) ||
             LanguageLevel.JDK_24_PREVIEW == useSiteLevel; //jep 494
    }

    override val standardLevel: LanguageLevel = LanguageLevel.JDK_25
  },

  /**
   * JEP 511
   * @see TRANSITIVE_DEPENDENCY_ON_JAVA_BASE
   * @see PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS
   */
  TRANSITIVE_DEPENDENCY_ON_JAVA_BASE(LanguageLevel.JDK_24_PREVIEW, "feature.package.transitive.dependency.on.java.base") { //jep 494
    override fun isSufficient(useSiteLevel: LanguageLevel): Boolean {
      return useSiteLevel.isAtLeast(LanguageLevel.JDK_25) ||
             LanguageLevel.JDK_24_PREVIEW == useSiteLevel //jep 494
    }

    override val standardLevel: LanguageLevel = LanguageLevel.JDK_25
  },

  /**
   * JEP 512
   * @see IMPLICIT_CLASSES
   * @see INSTANCE_MAIN_METHOD
   * @see IMPLICIT_CLASS_NAME_OUT_OF_SCOPE
   * @see INHERITED_STATIC_MAIN_METHOD
   * @see JAVA_LANG_IO
   */
  JAVA_LANG_IO(LanguageLevel.JDK_25, "feature.java.lang.io"),

  VALHALLA_VALUE_CLASSES(LanguageLevel.JDK_X, "feature.valhalla.value.classes"),
  ;

  /**
   * @return minimal language level where feature is available.
   * Note that this doesn't mean that the feature is available on every language level which is higher.
   * In most of the cases, `PsiUtil.isAvailable(PsiElement)` or [.isSufficient] should be used instead.
   */
  val minimumLevel: LanguageLevel

  private val myKey: @PropertyKey(resourceBundle = JavaSyntaxBundle.BUNDLE) String
  private val myCanBeCustomized: Boolean
  private val myObsoletePreviewLevels: Set<LanguageLevel>

  constructor(
    level: LanguageLevel,
    key: @PropertyKey(resourceBundle = JavaSyntaxBundle.BUNDLE) String,
    vararg obsoletePreviewLevels: LanguageLevel,
  ) {
    minimumLevel = level
    myKey = key
    myCanBeCustomized = false
    myObsoletePreviewLevels = mutableSetOf()
    for (obsoletePreviewLevel in obsoletePreviewLevels) {
      require(obsoletePreviewLevel.isUnsupported) { obsoletePreviewLevel.toString() }
      myObsoletePreviewLevels.add(obsoletePreviewLevel)
    }
  }

  constructor(
    level: LanguageLevel,
    key: @PropertyKey(resourceBundle = JavaSyntaxBundle.BUNDLE) String,
    canBeCustomized: Boolean = false,
  ) {
    minimumLevel = level
    myKey = key
    myCanBeCustomized = canBeCustomized
    myObsoletePreviewLevels = emptySet()
  }

  /**
   * Human-readable feature name
   */
  val featureName: @Nls String
    get() = message(myKey)

  /**
   * @return true if the availability of this feature can be additionally filtered using [LanguageFeatureProvider].
   */
  @Contract(pure = true)
  fun canBeCustomized(): Boolean {
    return myCanBeCustomized
  }

  @Contract(pure = true)
  open fun isSufficient(useSiteLevel: LanguageLevel): Boolean {
    return (useSiteLevel.isAtLeast(this.minimumLevel) ||
            useSiteLevel.isUnsupported && myObsoletePreviewLevels.contains(useSiteLevel)) &&
           (!minimumLevel.isPreview || useSiteLevel.isPreview)
  }

  @get:Contract(pure = true)
  open val isLimited: Boolean
    get() = false

  /**
   * Override if feature was preview and then accepted as standard
   */
  @get:Contract(pure = true)
  open val standardLevel: LanguageLevel?
    get() = if (minimumLevel.isPreview) null else this.minimumLevel

  companion object {
    // Values taken from jdk.internal.javac.PreviewFeature.Feature enum
    @Contract(pure = true)
    @JvmStatic
    fun convertFromPreviewFeatureName(feature: @NonNls String): JavaFeature? {
      return when (feature) {
        "PATTERN_MATCHING_IN_INSTANCEOF" -> PATTERNS
        "TEXT_BLOCKS" -> TEXT_BLOCKS
        "RECORDS" -> RECORDS
        "SEALED_CLASSES" -> SEALED_CLASSES
        "STRING_TEMPLATES" -> STRING_TEMPLATES
        "UNNAMED_CLASSES", "IMPLICIT_CLASSES" -> IMPLICIT_CLASSES
        "SCOPED_VALUES" -> SCOPED_VALUES
        "STABLE_VALUES" -> STABLE_VALUES
        "STRUCTURED_CONCURRENCY" -> STRUCTURED_CONCURRENCY
        "PEM_API" -> PEM_API
        "CLASSFILE_API" -> CLASSFILE_API
        "STREAM_GATHERERS" -> STREAM_GATHERERS
        "FOREIGN" -> FOREIGN_FUNCTIONS
        "VIRTUAL_THREADS" -> VIRTUAL_THREADS
        "MODULE_IMPORTS" -> MODULE_IMPORT_DECLARATIONS
        else -> null
      }
    }
  }
}
