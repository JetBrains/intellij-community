// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.java;

import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Key;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Represents a language level (i.e. features available) of a Java code.
 * The {@link org.jetbrains.jps.model.java.LanguageLevel} class is a compiler-side counterpart of this enum.
 *
 * @author dsl
 * @see LanguageLevelProjectExtension
 * @see LanguageLevelModuleExtension
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
  JDK_16_PREVIEW(JavaPsiBundle.messagePointer("jdk.16.preview.language.level.description"), 16),
  JDK_17(JavaPsiBundle.messagePointer("jdk.17.language.level.description"), 17),
  JDK_17_PREVIEW(JavaPsiBundle.messagePointer("jdk.17.preview.language.level.description"), 17),
  JDK_18(JavaPsiBundle.messagePointer("jdk.18.language.level.description"), 18),
  JDK_18_PREVIEW(JavaPsiBundle.messagePointer("jdk.18.preview.language.level.description"), 18),
  JDK_X(JavaPsiBundle.messagePointer("jdk.X.language.level.description"), 19);

  /**
   * Should point to the last released JDK.
   */
  public static final LanguageLevel HIGHEST = JDK_17;
  public static final Key<LanguageLevel> KEY = Key.create("LANGUAGE_LEVEL");

  private final Supplier<@Nls String> myPresentableText;
  private final JavaVersion myVersion;
  private final boolean myPreview;

  LanguageLevel(Supplier<@Nls String> presentableTextSupplier, int major) {
    myPresentableText = presentableTextSupplier;
    myVersion = JavaVersion.compose(major);
    myPreview = name().endsWith("_PREVIEW");
  }

  public boolean isPreview() {
    return myPreview;
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
}