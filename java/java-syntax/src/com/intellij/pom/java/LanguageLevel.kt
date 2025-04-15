// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.java

import com.intellij.java.syntax.JavaSyntaxBundle.messagePointer
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.lang.JavaVersion
import com.intellij.util.lang.JavaVersion.Companion.compose
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmField

/**
 * Represents a language level (i.e. features available) of a Java code.
 * The [org.jetbrains.jps.model.java.LanguageLevel] class is a compiler-side counterpart of this enum.
 *
 *
 * Unsupported language levels are marked as [ApiStatus.Obsolete] to draw attention. They should not be normally used,
 * except probably in rare tests and inside [JavaFeature].
 *
 * @see com.intellij.openapi.roots.LanguageLevelModuleExtension
 *
 * @see com.intellij.openapi.roots.LanguageLevelProjectExtension
 *
 * @see JavaSdkVersion
 *
 * @see JavaFeature
 */
enum class LanguageLevel {
  JDK_1_3(messagePointer("jdk.1.3.language.level.description"), 3),
  JDK_1_4(messagePointer("jdk.1.4.language.level.description"), 4),
  JDK_1_5(messagePointer("jdk.1.5.language.level.description"), 5),
  JDK_1_6(messagePointer("jdk.1.6.language.level.description"), 6),
  JDK_1_7(messagePointer("jdk.1.7.language.level.description"), 7),
  JDK_1_8(messagePointer("jdk.1.8.language.level.description"), 8),
  JDK_1_9(messagePointer("jdk.1.9.language.level.description"), 9),
  JDK_10(messagePointer("jdk.10.language.level.description"), 10),
  JDK_11(messagePointer("jdk.11.language.level.description"), 11),
  JDK_12(messagePointer("jdk.12.language.level.description"), 12),
  JDK_13(messagePointer("jdk.13.language.level.description"), 13),
  JDK_14(messagePointer("jdk.14.language.level.description"), 14),
  JDK_15(messagePointer("jdk.15.language.level.description"), 15),
  JDK_16(messagePointer("jdk.16.language.level.description"), 16),
  JDK_17(messagePointer("jdk.17.language.level.description"), 17),

  @ApiStatus.Obsolete
  JDK_17_PREVIEW(17),
  JDK_18(messagePointer("jdk.18.language.level.description"), 18),

  @ApiStatus.Obsolete
  JDK_18_PREVIEW(18),
  JDK_19(messagePointer("jdk.19.language.level.description"), 19),

  @ApiStatus.Obsolete
  JDK_19_PREVIEW(19),
  JDK_20(messagePointer("jdk.20.language.level.description"), 20),

  @ApiStatus.Obsolete
  JDK_20_PREVIEW(20),
  JDK_21(messagePointer("jdk.21.language.level.description"), 21),
  JDK_21_PREVIEW(messagePointer("jdk.21.preview.language.level.description"), 21),
  JDK_22(messagePointer("jdk.22.language.level.description"), 22),
  JDK_22_PREVIEW(messagePointer("jdk.22.preview.language.level.description"), 22),
  JDK_23(messagePointer("jdk.23.language.level.description"), 23),
  JDK_23_PREVIEW(messagePointer("jdk.23.preview.language.level.description"), 23),
  JDK_24(messagePointer("jdk.24.language.level.description"), 24),
  JDK_24_PREVIEW(messagePointer("jdk.24.preview.language.level.description"), 24),
  JDK_X(messagePointer("jdk.X.language.level.description"), 25),
  ;

  private val myPresentableText: () -> @Nls String
  private val myVersion: JavaVersion
  val isPreview: Boolean

  /**
   * @return true if this language level is not supported anymore. It's still possible to invoke compiler or launch the program
   * using this language level. However, it's not guaranteed that the code insight features will work correctly.
   */
  val isUnsupported: Boolean

  /**
   * Construct the language level for a supported Java version
   *
   * @param presentableTextSupplier a supplier that returns the language level description
   * @param major the major version number. Whether the version is a preview version is determined by the enum constant name
   */
  constructor(presentableTextSupplier: () -> @Nls String, major: Int) {
    myPresentableText = presentableTextSupplier
    myVersion = compose(major)
    this.isUnsupported = false
    this.isPreview = name.endsWith("_PREVIEW") || name.endsWith("_X")
  }

  /**
   * Construct the language level for an unsupported Java version
   *
   * @param major the major version number. Unsupported Java version is always a preview version
   */
  constructor(major: Int) {
    myPresentableText = messagePointer("jdk.unsupported.preview.language.level.description", major)
    myVersion = compose(major)
    this.isUnsupported = true
    this.isPreview = true
    require(name.endsWith("_PREVIEW")) { "Only preview versions could be unsupported: " + name }
  }

  /**
   * @return corresponding preview level, or `null` if level has no paired preview level
   */
  fun getPreviewLevel(): LanguageLevel? {
    if (isPreview) return this
    try {
      return valueOf(name + "_PREVIEW")
    }
    catch (_: IllegalArgumentException) {
      return null
    }
  }

  /**
   * @return corresponding non-preview level; this if this level is non-preview already
   */
  fun getNonPreviewLevel(): LanguageLevel {
    if (!isPreview) return this
    return requireNotNull(ourStandardVersions[myVersion.feature])
  }

  val presentableText: @Nls String
    get() = myPresentableText()

  /**
   * @param level level to compare to
   * @return true, if this language level is at least the same or newer than the level we are comparing to.
   * A preview level for Java version X is assumed to be between non-preview version X and non-preview version X+1
   */
  fun isAtLeast(level: LanguageLevel): Boolean {
    return compareTo(level) >= 0
  }

  /**
   * @param level level to compare to
   * @return true if this language level is strictly less than the level we are comparing to.
   * A preview level for Java version X is assumed to be between non-preview version X and non-preview version X+1
   */
  fun isLessThan(level: LanguageLevel): Boolean {
    return compareTo(level) < 0
  }

  /**
   * @return the [JavaVersion] object that corresponds to this language level
   */
  fun toJavaVersion(): JavaVersion {
    return myVersion
  }

  /**
   * @return the language level feature number (like 8 for [.JDK_1_8]).
   */
  fun feature(): Int {
    return myVersion.feature
  }

  /**
   * @return short representation of the corresponding language level, like '8', or '21-preview'
   */
  val shortText: @NlsSafe String
    get() {
      if (this == JDK_X) {
        return "X"
      }
      val feature = feature()
      if (feature < 5) {
        return "1.$feature"
      }
      return feature.toString() + (if (this.isPreview) "-preview" else "")
    }

  companion object {
    /**
     * Should point to the latest released JDK.
     */
    @JvmField
    val HIGHEST: LanguageLevel = JDK_24

    private val ourStandardVersions: Map<Int, LanguageLevel> = LanguageLevel.entries.asSequence()
      .filterNot { ver -> ver.isPreview }
      .associateBy { ver -> ver.myVersion.feature }

    /** See [JavaVersion.parse] for supported formats.  */
    @JvmStatic
    fun parse(compilerComplianceOption: String?): LanguageLevel? {
      if (compilerComplianceOption != null) {
        val sdkVersion = JavaSdkVersion.fromVersionString(compilerComplianceOption)
        if (sdkVersion != null) {
          return sdkVersion.maxLanguageLevel
        }
      }
      return null
    }

    /**
     * @param feature major Java language level number
     * @return a [LanguageLevel] constant that correspond to the specified level (non-preview).
     * Returns null for unknown/unsupported input. May return [.JDK_X] if language level is one level
     * higher than maximal supported.
     */
    @JvmStatic
    fun forFeature(feature: Int): LanguageLevel? {
      return ourStandardVersions[feature]
    }
  }
}