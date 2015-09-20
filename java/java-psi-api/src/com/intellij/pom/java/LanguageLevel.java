/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.pom.java;

import com.intellij.core.JavaCoreBundle;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dsl
 * @see LanguageLevelProjectExtension
 * @see LanguageLevelModuleExtension
 */
public enum LanguageLevel {
  JDK_1_3("Java 1.3", JavaCoreBundle.message("jdk.1.3.language.level.description"), "1.3"),
  JDK_1_4("Java 1.4", JavaCoreBundle.message("jdk.1.4.language.level.description"), "1.4"),
  JDK_1_5("Java 5.0", JavaCoreBundle.message("jdk.1.5.language.level.description"), "1.5", "5"),
  JDK_1_6("Java 6", JavaCoreBundle.message("jdk.1.6.language.level.description"), "1.6", "6"),
  JDK_1_7("Java 7", JavaCoreBundle.message("jdk.1.7.language.level.description"), "1.7", "7"),
  JDK_1_8("Java 8", JavaCoreBundle.message("jdk.1.8.language.level.description"), "1.8", "8"),
  JDK_1_9("Java 9", JavaCoreBundle.message("jdk.1.9.language.level.description"), "1.9", "9"),
  JDK_X("Java X", JavaCoreBundle.message("jdk.X.language.level.description"), "");

  public static final LanguageLevel HIGHEST = JDK_1_8; // TODO! when language level 9 is really supported, update this field
  public static final Key<LanguageLevel> KEY = Key.create("LANGUAGE_LEVEL");

  private final String myName;
  private final String myPresentableText;
  private final String myCompilerComplianceDefaultOption;
  private final String[] myCompilerComplianceOptionVariants;

  /**
   * @param compilerComplianceAlternativeOptions synonyms supported by javac -source parameter (see http://docs.oracle.com/javase/8/docs/technotes/tools/windows/javac.html)
   */
  LanguageLevel(@NotNull String name, @NotNull @Nls String presentableText, @NotNull String compilerComplianceDefaultOption,
                @NotNull String... compilerComplianceAlternativeOptions) {
    myName = name;
    myPresentableText = presentableText;
    myCompilerComplianceDefaultOption = compilerComplianceDefaultOption;
    myCompilerComplianceOptionVariants = ArrayUtil.prepend(compilerComplianceDefaultOption, compilerComplianceAlternativeOptions);
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
    return myCompilerComplianceDefaultOption;
  }

  /**
   * Parses string accordingly to format of '-source' parameter of javac. Synonyms ("8" for "1.8") are supported.
   */
  @Nullable
  public static LanguageLevel parse(@Nullable String compilerComplianceOption) {
    if (StringUtil.isEmpty(compilerComplianceOption)) return null;

    for (LanguageLevel level : values()) {
      if (ArrayUtil.contains(compilerComplianceOption, level.myCompilerComplianceOptionVariants)) {
        return level;
      }
    }
    return null;
  }
}
