// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.java;

import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Represents a language level (i.e. features available) of a Java code.
 * The {@link org.jetbrains.jps.model.java.LanguageLevel} class is a compiler-side counterpart of this enum.
 *
 * @see com.intellij.openapi.roots.LanguageLevelModuleExtension
 * @see com.intellij.openapi.roots.LanguageLevelProjectExtension
 * @see JavaSdkVersion
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
  JDK_18(JavaPsiBundle.messagePointer("jdk.18.language.level.description"), 18),
  JDK_19(JavaPsiBundle.messagePointer("jdk.19.language.level.description"), 19),
  JDK_20(JavaPsiBundle.messagePointer("jdk.20.language.level.description"), 20),
  JDK_20_PREVIEW(JavaPsiBundle.messagePointer("jdk.20.preview.language.level.description"), 20),
  JDK_21(JavaPsiBundle.messagePointer("jdk.21.language.level.description"), 21),
  JDK_21_PREVIEW(JavaPsiBundle.messagePointer("jdk.21.preview.language.level.description"), 21),
  JDK_22(JavaPsiBundle.messagePointer("jdk.22.language.level.description"), 22),
  JDK_22_PREVIEW(JavaPsiBundle.messagePointer("jdk.22.preview.language.level.description"), 22),
  JDK_X(JavaPsiBundle.messagePointer("jdk.X.language.level.description"), 23),

  // Unsupported
  // Marked as obsolete to draw attention, as they should not be normally used in code or in tests,
  // except the tests that explicitly test the obsolete levels
  
  @ApiStatus.Obsolete
  JDK_17_PREVIEW(17, JDK_20_PREVIEW),
  @ApiStatus.Obsolete
  JDK_18_PREVIEW(18, JDK_20_PREVIEW),
  @ApiStatus.Obsolete
  JDK_19_PREVIEW(19, JDK_20_PREVIEW),
  ;

  /**
   * Should point to the latest released JDK.
   */
  public static final LanguageLevel HIGHEST = JDK_21;

  private final Supplier<@Nls String> myPresentableText;
  private final JavaVersion myVersion;
  private final boolean myPreview;
  private final @Nullable LanguageLevel myAlias;

  LanguageLevel(Supplier<@Nls String> presentableTextSupplier, int major) {
    this(presentableTextSupplier, major, null);
  }

  LanguageLevel(int major, @NotNull LanguageLevel alias) {
    this(JavaPsiBundle.messagePointer("jdk.unsupported.preview.language.level.description", major), major, alias);
  }

  LanguageLevel(Supplier<@Nls String> presentableTextSupplier, int major, @Nullable LanguageLevel alias) {
    myPresentableText = presentableTextSupplier;
    myVersion = JavaVersion.compose(major);
    if (alias != null && alias.isUnsupported()) {
      throw new IllegalArgumentException("Cannot alias to unsupported version");
    }
    myAlias = alias;
    myPreview = name().endsWith("_PREVIEW") || name().endsWith("_X");
  }

  public boolean isPreview() {
    return myPreview;
  }

  /**
   * @return true if this language level is not supported anymore. It's still possible to invoke compiler or launch the program
   * using this language level. However, it's not guaranteed that the code insight features will work correctly.
   * All the code insight features will use the {@linkplain #getSupportedLevel() alias level} instead
   */
  public boolean isUnsupported() {
    return myAlias != null;
  }

  /**
   * @return the closest supported language level for the unsupported level;
   * returns this for supported level
   */
  @ApiStatus.Experimental
  public @NotNull LanguageLevel getSupportedLevel() {
    return myAlias == null ? this : myAlias;
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
    return valueOf(StringUtil.substringBefore(name(), "_PREVIEW"));
  }

  @NotNull
  @Nls
  public String getPresentableText() {
    return myPresentableText.get();
  }

  public boolean isAtLeast(@NotNull LanguageLevel level) {
    return compareTo(level) >= 0;
  }

  public boolean isLessThan(@NotNull LanguageLevel level) {
    return compareTo(level) < 0;
  }

  @NotNull
  public JavaVersion toJavaVersion() {
    return myVersion;
  }

  /** See {@link JavaVersion#parse(String)} for supported formats. */
  @Nullable
  public static LanguageLevel parse(@Nullable String compilerComplianceOption) {
    if (compilerComplianceOption != null) {
      JavaSdkVersion sdkVersion = JavaSdkVersion.fromVersionString(compilerComplianceOption);
      if (sdkVersion != null) {
        return sdkVersion.getMaxLanguageLevel();
      }
    }
    return null;
  }

  public static final Key<LanguageLevel> FILE_LANGUAGE_LEVEL_KEY = Key.create("FORCE_LANGUAGE_LEVEL");
}