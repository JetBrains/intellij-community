/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.pom.java;

import com.intellij.core.JavaCoreBundle;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Key;
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
  JDK_1_3("Java 1.3", JavaCoreBundle.message("jdk.1.3.language.level.description"), "1.3"),
  JDK_1_4("Java 1.4", JavaCoreBundle.message("jdk.1.4.language.level.description"), "1.4"),
  JDK_1_5("Java 5.0", JavaCoreBundle.message("jdk.1.5.language.level.description"), "1.5", "5"),
  JDK_1_6("Java 6", JavaCoreBundle.message("jdk.1.6.language.level.description"), "1.6", "6"),
  JDK_1_7("Java 7", JavaCoreBundle.message("jdk.1.7.language.level.description"), "1.7", "7"),
  JDK_1_8("Java 8", JavaCoreBundle.message("jdk.1.8.language.level.description"), "1.8", "8"),
  JDK_1_9("Java 9", JavaCoreBundle.message("jdk.1.9.language.level.description"), "9", "1.9"),
  JDK_X("Java X", JavaCoreBundle.message("jdk.X.language.level.description"), "");

  public static final LanguageLevel HIGHEST = JDK_1_9;
  public static final Key<LanguageLevel> KEY = Key.create("LANGUAGE_LEVEL");

  private final String myName;
  private final String myPresentableText;
  private final String[] myCompilerComplianceOptions;

  /**
   * @param compilerComplianceOptions versions supported by Javac '-source' parameter
   * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javac.html">Javac Reference</a>
   */
  LanguageLevel(String name, @Nls String presentableText, String... compilerComplianceOptions) {
    myName = name;
    myPresentableText = presentableText;
    myCompilerComplianceOptions = compilerComplianceOptions;
  }

  @NotNull
  public String getName() {
    return myName;
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

  /**
   * String representation of the level, suitable to pass as a value of compiler's "-source" and "-target" options
   */
  public String getCompilerComplianceDefaultOption() {
    return myCompilerComplianceOptions[0];
  }

  /** See {@link com.intellij.util.lang.JavaVersion#parse(String)} for supported formats. */
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