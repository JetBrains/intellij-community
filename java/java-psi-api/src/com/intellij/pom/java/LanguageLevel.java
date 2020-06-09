// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.java;

import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Key;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  JDK_1_3(JavaPsiBundle.message("jdk.1.3.language.level.description"), 3),
  JDK_1_4(JavaPsiBundle.message("jdk.1.4.language.level.description"), 4),
  JDK_1_5(JavaPsiBundle.message("jdk.1.5.language.level.description"), 5),
  JDK_1_6(JavaPsiBundle.message("jdk.1.6.language.level.description"), 6),
  JDK_1_7(JavaPsiBundle.message("jdk.1.7.language.level.description"), 7),
  JDK_1_8(JavaPsiBundle.message("jdk.1.8.language.level.description"), 8),
  JDK_1_9(JavaPsiBundle.message("jdk.1.9.language.level.description"), 9),
  JDK_10(JavaPsiBundle.message("jdk.10.language.level.description"), 10),
  JDK_11(JavaPsiBundle.message("jdk.11.language.level.description"), 11),
  JDK_12(JavaPsiBundle.message("jdk.12.language.level.description"), 12),
  JDK_13(JavaPsiBundle.message("jdk.13.language.level.description"), 13),
  JDK_14(JavaPsiBundle.message("jdk.14.language.level.description"), 14),
  JDK_14_PREVIEW(JavaPsiBundle.message("jdk.14.preview.language.level.description"), 14),
  JDK_15(JavaPsiBundle.message("jdk.15.language.level.description"), 15),
  JDK_15_PREVIEW(JavaPsiBundle.message("jdk.15.preview.language.level.description"), 15),
  
  JDK_X(JavaPsiBundle.message("jdk.X.language.level.description"), 16);

  public static final LanguageLevel HIGHEST = JDK_14;
  public static final Key<LanguageLevel> KEY = Key.create("LANGUAGE_LEVEL");

  private final String myPresentableText;
  private final JavaVersion myVersion;
  private final boolean myPreview;

  LanguageLevel(@Nls String presentableText, int major) {
    myPresentableText = presentableText;
    myVersion = JavaVersion.compose(major);
    myPreview = name().endsWith("_PREVIEW");
  }

  public boolean isPreview() {
    return myPreview;
  }

  @NotNull
  @Nls
  public String getPresentableText() {
    return myPresentableText;
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

  /** @deprecated use {@link org.jetbrains.jps.model.java.JpsJavaSdkType#complianceOption(JavaVersion)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public String getCompilerComplianceDefaultOption() {
    return myVersion.feature <= 8 ? "1." + myVersion.feature : String.valueOf(myVersion.feature);
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