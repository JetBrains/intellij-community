// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.java;

import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a language level (i.e. features available) of a Java code.
 * The {@link org.jetbrains.jps.model.java.LanguageLevel} class is a compiler-side counterpart of this enum.
 * <p>
 * Unsupported language levels are marked as {@link ApiStatus.Obsolete} to draw attention. They should not be normally used,
 * except probably in rare tests and inside {@link JavaFeature}.
 *
 * @see com.intellij.openapi.roots.LanguageLevelModuleExtension
 * @see com.intellij.openapi.roots.LanguageLevelProjectExtension
 * @see JavaSdkVersion
 * @see JavaFeature
 */
public enum LanguageLevel {
  JDK_1_3(JavaPsiBundle.messagePointer("jdk.1.3.language.level.description"), 3),
  JDK_1_4(JavaPsiBundle.messagePointer("jdk.1.4.language.level.description"), 4),
  JDK_1_5(JavaPsiBundle.messagePointer("jdk.1.5.language.level.description"), 5),
  JDK_1_6(JavaPsiBundle.messagePointer("jdk.1.6.language.level.description"), 6),
  JDK_1_7(JavaPsiBundle.messagePointer("jdk.1.7.language.level.description"), 7),
  JDK_1_8(JavaPsiBundle.messagePointer("jdk.1.8.language.level.description"), 8),
  JDK_1_9(JavaPsiBundle.messagePointer("jdk.1.9.language.level.description"), 9),
  JDK_10(JavaPsiBundle.messagePointer("jdk.10.language.level.description"), 10),
  JDK_11(JavaPsiBundle.messagePointer("jdk.11.language.level.description"), 11),
  JDK_12(JavaPsiBundle.messagePointer("jdk.12.language.level.description"), 12),
  JDK_13(JavaPsiBundle.messagePointer("jdk.13.language.level.description"), 13),
  JDK_14(JavaPsiBundle.messagePointer("jdk.14.language.level.description"), 14),
  JDK_15(JavaPsiBundle.messagePointer("jdk.15.language.level.description"), 15),
  JDK_16(JavaPsiBundle.messagePointer("jdk.16.language.level.description"), 16),
  JDK_17(JavaPsiBundle.messagePointer("jdk.17.language.level.description"), 17),
  @ApiStatus.Obsolete
  JDK_17_PREVIEW(17),
  JDK_18(JavaPsiBundle.messagePointer("jdk.18.language.level.description"), 18),
  @ApiStatus.Obsolete
  JDK_18_PREVIEW(18),
  JDK_19(JavaPsiBundle.messagePointer("jdk.19.language.level.description"), 19),
  @ApiStatus.Obsolete
  JDK_19_PREVIEW(19),
  JDK_20(JavaPsiBundle.messagePointer("jdk.20.language.level.description"), 20),
  @ApiStatus.Obsolete
  JDK_20_PREVIEW(20),
  JDK_21(JavaPsiBundle.messagePointer("jdk.21.language.level.description"), 21),
  JDK_21_PREVIEW(JavaPsiBundle.messagePointer("jdk.21.preview.language.level.description"), 21),
  JDK_22(JavaPsiBundle.messagePointer("jdk.22.language.level.description"), 22),
  JDK_22_PREVIEW(JavaPsiBundle.messagePointer("jdk.22.preview.language.level.description"), 22),
  JDK_23(JavaPsiBundle.messagePointer("jdk.23.language.level.description"), 23),
  JDK_23_PREVIEW(JavaPsiBundle.messagePointer("jdk.23.preview.language.level.description"), 23),
  JDK_24(JavaPsiBundle.messagePointer("jdk.24.language.level.description"), 24),
  JDK_24_PREVIEW(JavaPsiBundle.messagePointer("jdk.24.preview.language.level.description"), 24),
  JDK_X(JavaPsiBundle.messagePointer("jdk.X.language.level.description"), 25),
  ;

  /**
   * Should point to the latest released JDK.
   */
  public static final LanguageLevel HIGHEST = JDK_23;

  private final Supplier<@Nls String> myPresentableText;
  private final JavaVersion myVersion;
  private final boolean myPreview;
  private final boolean myUnsupported;
  private static final Map<Integer, LanguageLevel> ourStandardVersions =
    Stream.of(values()).filter(ver -> !ver.isPreview())
      .collect(Collectors.toMap(ver -> ver.myVersion.feature, Function.identity()));

  /**
   * Construct the language level for a supported Java version
   * 
   * @param presentableTextSupplier a supplier that returns the language level description
   * @param major the major version number. Whether the version is a preview version is determined by the enum constant name
   */
  LanguageLevel(Supplier<@Nls String> presentableTextSupplier, int major) {
    myPresentableText = presentableTextSupplier;
    myVersion = JavaVersion.compose(major);
    myUnsupported = false;
    myPreview = name().endsWith("_PREVIEW") || name().endsWith("_X");
  }

  /**
   * Construct the language level for an unsupported Java version
   * 
   * @param major the major version number. Unsupported Java version is always a preview version
   */
  LanguageLevel(int major) {
    myPresentableText = JavaPsiBundle.messagePointer("jdk.unsupported.preview.language.level.description", major);
    myVersion = JavaVersion.compose(major);
    myUnsupported = true;
    myPreview = true;
    if (!name().endsWith("_PREVIEW")) {
      throw new IllegalArgumentException("Only preview versions could be unsupported: " + name());
    }
  }

  public boolean isPreview() {
    return myPreview;
  }

  /**
   * @return true if this language level is not supported anymore. It's still possible to invoke compiler or launch the program
   * using this language level. However, it's not guaranteed that the code insight features will work correctly.
   */
  public boolean isUnsupported() {
    return myUnsupported;
  }

  /**
   * @return corresponding preview level, or {@code null} if level has no paired preview level
   */
  public @Nullable LanguageLevel getPreviewLevel() {
    if (myPreview) return this;
    try {
      return valueOf(name() + "_PREVIEW");
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * @return corresponding non-preview level; this if this level is non-preview already
   */
  public @NotNull LanguageLevel getNonPreviewLevel() {
    if (!myPreview) return this;
    return Objects.requireNonNull(ourStandardVersions.get(myVersion.feature));
  }

  public @NotNull @Nls String getPresentableText() {
    return myPresentableText.get();
  }

  /**
   * @param level level to compare to
   * @return true, if this language level is at least the same or newer than the level we are comparing to.
   * A preview level for Java version X is assumed to be between non-preview version X and non-preview version X+1
   */
  public boolean isAtLeast(@NotNull LanguageLevel level) {
    return compareTo(level) >= 0;
  }

  /**
   * @param level level to compare to
   * @return true if this language level is strictly less than the level we are comparing to.
   * A preview level for Java version X is assumed to be between non-preview version X and non-preview version X+1
   */
  public boolean isLessThan(@NotNull LanguageLevel level) {
    return compareTo(level) < 0;
  }

  /**
   * @return the {@link JavaVersion} object that corresponds to this language level 
   */
  public @NotNull JavaVersion toJavaVersion() {
    return myVersion;
  }

  /**
   * @return the language level feature number (like 8 for {@link #JDK_1_8}).
   */
  public int feature() {
    return myVersion.feature;
  }

  /**
   * @return short representation of the corresponding language level, like '8', or '21-preview'
   */
  public @NlsSafe String getShortText() {
    if (this == JDK_X) {
      return "X";
    }
    int feature = feature();
    if (feature < 5) {
      return "1." + feature;
    }
    return feature + (isPreview() ? "-preview" : "");
  }

  /** See {@link JavaVersion#parse(String)} for supported formats. */
  public static @Nullable LanguageLevel parse(@Nullable String compilerComplianceOption) {
    if (compilerComplianceOption != null) {
      JavaSdkVersion sdkVersion = JavaSdkVersion.fromVersionString(compilerComplianceOption);
      if (sdkVersion != null) {
        return sdkVersion.getMaxLanguageLevel();
      }
    }
    return null;
  }

  /**
   * @param feature major Java language level number
   * @return a {@link LanguageLevel} constant that correspond to the specified level (non-preview).
   * Returns null for unknown/unsupported input. May return {@link #JDK_X} if language level is one level
   * higher than maximal supported.
   */
  public static @Nullable LanguageLevel forFeature(int feature) {
    return ourStandardVersions.get(feature);
  }

  public static final Key<LanguageLevel> FILE_LANGUAGE_LEVEL_KEY = Key.create("FORCE_LANGUAGE_LEVEL");
}