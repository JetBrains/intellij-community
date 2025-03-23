// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Represents a version of Java SDK. Use {@code JavaSdk#getVersion(Sdk)} method to obtain a version of an {@code Sdk}.
 * @see LanguageLevel
 */
public enum JavaSdkVersion {
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

  private final LanguageLevel myMaxLanguageLevel;

  JavaSdkVersion(@NotNull LanguageLevel maxLanguageLevel) {
    myMaxLanguageLevel = maxLanguageLevel;
  }

  public @NotNull LanguageLevel getMaxLanguageLevel() {
    return myMaxLanguageLevel;
  }

  public @NotNull @NlsSafe String getDescription() {
    int feature = ordinal();
    return feature < 5 ? "1." + feature : String.valueOf(feature);
  }

  public boolean isAtLeast(@NotNull JavaSdkVersion version) {
    return compareTo(version) >= 0;
  }

  public static @NotNull JavaSdkVersion fromLanguageLevel(@NotNull LanguageLevel languageLevel) throws IllegalArgumentException {
    JavaSdkVersion[] values = values();
    if (languageLevel == LanguageLevel.JDK_X) {
      return values[values.length - 1];
    }
    int feature = languageLevel.feature();
    if (feature < values.length) {
      return values[feature];
    }
    throw new IllegalArgumentException("Can't map " + languageLevel + " to any of " + Arrays.toString(values));
  }

  /** See {@link JavaVersion#parse(String)} for supported formats. */
  public static @Nullable JavaSdkVersion fromVersionString(@NotNull String versionString) {
    JavaVersion version = JavaVersion.tryParse(versionString);
    return version != null ? fromJavaVersion(version) : null;
  }

  public static @Nullable JavaSdkVersion fromJavaVersion(@NotNull JavaVersion version) {
    JavaSdkVersion[] values = values();
    return version.feature < values.length ? values[version.feature] : null;
  }
}