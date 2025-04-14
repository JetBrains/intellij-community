// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots

import com.intellij.openapi.util.NlsSafe
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.lang.JavaVersion
import com.intellij.util.lang.JavaVersion.Companion.tryParse

/**
 * Represents a version of Java SDK. Use `JavaSdk#getVersion(Sdk)` method to obtain a version of an `Sdk`.
 * @see LanguageLevel
 */
enum class JavaSdkVersion(val maxLanguageLevel: LanguageLevel) {
  JDK_1_0(LanguageLevel.JDK_1_3),
  JDK_1_1(LanguageLevel.JDK_1_3),
  JDK_1_2(LanguageLevel.JDK_1_3),
  JDK_1_3(LanguageLevel.JDK_1_3),
  JDK_1_4(LanguageLevel.JDK_1_4),
  JDK_1_5(LanguageLevel.JDK_1_5),
  JDK_1_6(LanguageLevel.JDK_1_6),
  JDK_1_7(LanguageLevel.JDK_1_7),
  JDK_1_8(LanguageLevel.JDK_1_8),
  JDK_1_9(LanguageLevel.JDK_1_9),
  JDK_10(LanguageLevel.JDK_10),
  JDK_11(LanguageLevel.JDK_11),
  JDK_12(LanguageLevel.JDK_12),
  JDK_13(LanguageLevel.JDK_13),
  JDK_14(LanguageLevel.JDK_14),
  JDK_15(LanguageLevel.JDK_15),
  JDK_16(LanguageLevel.JDK_16),
  JDK_17(LanguageLevel.JDK_17),
  JDK_18(LanguageLevel.JDK_18),
  JDK_19(LanguageLevel.JDK_19),
  JDK_20(LanguageLevel.JDK_20),
  JDK_21(LanguageLevel.JDK_21),
  JDK_22(LanguageLevel.JDK_22),
  JDK_23(LanguageLevel.JDK_23),
  JDK_24(LanguageLevel.JDK_24),
  JDK_25(LanguageLevel.JDK_X);

  val description: @NlsSafe String
    get() {
      val feature = ordinal
      return if (feature < 5) "1.$feature" else feature.toString()
    }

  fun isAtLeast(version: JavaSdkVersion): Boolean {
    return compareTo(version) >= 0
  }

  companion object {
    @Throws(IllegalArgumentException::class)
    @JvmStatic
    fun fromLanguageLevel(languageLevel: LanguageLevel): JavaSdkVersion {
      val values = JavaSdkVersion.entries.toTypedArray()
      if (languageLevel == LanguageLevel.JDK_X) {
        return values[values.size - 1]
      }
      val feature = languageLevel.feature()
      if (feature < values.size) {
        return values[feature]
      }
      throw IllegalArgumentException("Can't map " + languageLevel + " to any of " + values.contentToString())
    }

    /** See [JavaVersion.parse] for supported formats.  */
    @JvmStatic
    fun fromVersionString(versionString: String): JavaSdkVersion? {
      val version = tryParse(versionString)
      return if (version != null) fromJavaVersion(version) else null
    }

    @JvmStatic
    fun fromJavaVersion(version: JavaVersion): JavaSdkVersion? {
      val values = JavaSdkVersion.entries.toTypedArray()
      return if (version.feature < values.size) values[version.feature] else null
    }
  }
}