// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Represents version of Java SDK. Use {@code JavaSdk#getVersion(Sdk)} method to obtain version of an {@code Sdk}.
 *
 * @author nik
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
  JDK_10(LanguageLevel.JDK_10);

  private final LanguageLevel myMaxLanguageLevel;

  JavaSdkVersion(@NotNull LanguageLevel maxLanguageLevel) {
    myMaxLanguageLevel = maxLanguageLevel;
  }

  @NotNull
  public LanguageLevel getMaxLanguageLevel() {
    return myMaxLanguageLevel;
  }

  @NotNull
  public String getDescription() {
    int feature = ordinal();
    return feature < 5 ? "1." + feature : String.valueOf(feature);
  }

  public boolean isAtLeast(@NotNull JavaSdkVersion version) {
    return compareTo(version) >= 0;
  }

  @NotNull
  public static JavaSdkVersion fromLanguageLevel(@NotNull LanguageLevel languageLevel) throws IllegalArgumentException {
    if (languageLevel == LanguageLevel.JDK_1_3) {
      return JDK_1_3;
    }
    JavaSdkVersion[] values = values();
    if (languageLevel == LanguageLevel.JDK_X) {
      return values[values.length - 1];
    }
    for (JavaSdkVersion version : values) {
      if (version.getMaxLanguageLevel().isAtLeast(languageLevel)) {
        return version;
      }
    }
    throw new IllegalArgumentException("Can't map " + languageLevel + " to any of " + Arrays.toString(values));
  }

  /** See {@link JavaVersion#parse(String)} for supported formats. */
  @Nullable
  public static JavaSdkVersion fromVersionString(@NotNull String versionString) {
    JavaVersion version = JavaVersion.tryParse(versionString);
    return version != null ? fromJavaVersion(version) : null;
  }

  @Nullable
  public static JavaSdkVersion fromJavaVersion(@NotNull JavaVersion version) {
    JavaSdkVersion[] values = values();
    return version.feature < values.length ? values[version.feature] : null;
  }
}