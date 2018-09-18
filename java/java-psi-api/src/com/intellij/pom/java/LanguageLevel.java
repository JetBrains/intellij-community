// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom.java;

import com.intellij.core.JavaCoreBundle;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Key;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a language level (i.e. features available) of a Java code.
 * {@code org.jetbrains.jps.model.java.LanguageLevel} is a compiler-side counterpart of this enum.
 *
 * @author dsl
 * @see LanguageLevelProjectExtension
 * @see LanguageLevelModuleExtension
 * @see JavaSdkVersion
 */
public enum LanguageLevel {
  JDK_1_3(JavaCoreBundle.message("jdk.1.3.language.level.description"), 3),
  JDK_1_4(JavaCoreBundle.message("jdk.1.4.language.level.description"), 4),
  JDK_1_5(JavaCoreBundle.message("jdk.1.5.language.level.description"), 5),
  JDK_1_6(JavaCoreBundle.message("jdk.1.6.language.level.description"), 6),
  JDK_1_7(JavaCoreBundle.message("jdk.1.7.language.level.description"), 7),
  JDK_1_8(JavaCoreBundle.message("jdk.1.8.language.level.description"), 8),
  JDK_1_9(JavaCoreBundle.message("jdk.1.9.language.level.description"), 9),
  JDK_10(JavaCoreBundle.message("jdk.10.language.level.description"), 10),
  JDK_11(JavaCoreBundle.message("jdk.11.language.level.description"), 11),
  JDK_X(JavaCoreBundle.message("jdk.X.language.level.description"), 12);

  public static final LanguageLevel HIGHEST = JDK_10;
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

  /** @deprecated use {@link org.jetbrains.jps.model.java.JpsJavaSdkType#complianceOption()} (to be removed in IDEA 2019) */
  @Deprecated
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