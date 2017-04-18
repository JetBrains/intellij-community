/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots;

import com.intellij.pom.java.LanguageLevel;
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
  JDK_1_0(LanguageLevel.JDK_1_3, new String[]{"1.0"}),
  JDK_1_1(LanguageLevel.JDK_1_3, new String[]{"1.1"}),
  JDK_1_2(LanguageLevel.JDK_1_3, new String[]{"1.2"}),
  JDK_1_3(LanguageLevel.JDK_1_3, new String[]{"1.3"}),
  JDK_1_4(LanguageLevel.JDK_1_4, new String[]{"1.4"}),
  JDK_1_5(LanguageLevel.JDK_1_5, new String[]{"1.5", "5.0"}),
  JDK_1_6(LanguageLevel.JDK_1_6, new String[]{"1.6", "6.0"}),
  JDK_1_7(LanguageLevel.JDK_1_7, new String[]{"1.7", "7.0"}),
  JDK_1_8(LanguageLevel.JDK_1_8, new String[]{"1.8", "8.0"}),
  JDK_1_9(LanguageLevel.JDK_1_9, new String[]{"1.9", "9.0", "9-ea"});

  private static final JavaSdkVersion MAX_JDK = JDK_1_9;

  private final LanguageLevel myMaxLanguageLevel;
  private final String[] myVersionStrings;

  JavaSdkVersion(@NotNull LanguageLevel maxLanguageLevel, @NotNull String[] description) {
    myMaxLanguageLevel = maxLanguageLevel;
    myVersionStrings = description;
  }

  @NotNull
  public LanguageLevel getMaxLanguageLevel() {
    return myMaxLanguageLevel;
  }

  @NotNull
  public String getDescription() {
    return myVersionStrings[0];
  }

  public boolean isAtLeast(@NotNull JavaSdkVersion version) {
    return compareTo(version) >= 0;
  }

  @Override
  public String toString() {
    return super.toString() + ", description: " + getDescription();
  }

  @NotNull
  public static JavaSdkVersion fromLanguageLevel(@NotNull LanguageLevel languageLevel) throws IllegalArgumentException {
    if (languageLevel == LanguageLevel.JDK_1_3) {
      return JDK_1_3;
    }
    if (languageLevel == LanguageLevel.JDK_X) {
      return MAX_JDK;
    }
    for (JavaSdkVersion version : values()) {
      if (version.getMaxLanguageLevel().isAtLeast(languageLevel)) {
        return version;
      }
    }
    throw new IllegalArgumentException(
      "Can't map Java SDK by language level " + languageLevel + ". Available values: " + Arrays.toString(values())
    );
  }

  @Nullable
  public static JavaSdkVersion fromVersionString(@NotNull String versionString) {
    for (JavaSdkVersion version : values()) {
      for (String pattern : version.myVersionStrings) {
        if (versionString.contains(pattern)) {
          return version;
        }
      }
    }
    return null;
  }
}