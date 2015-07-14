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
package com.intellij.openapi.projectRoots;

import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Represents version of Java SDK. Use {@link JavaSdk#getVersion(Sdk)} method to obtain version of an {@link Sdk}
 *
 * @author nik
 */
public enum JavaSdkVersion {
  JDK_1_0(LanguageLevel.JDK_1_3, "1.0"),
  JDK_1_1(LanguageLevel.JDK_1_3, "1.1"),
  JDK_1_2(LanguageLevel.JDK_1_3, "1.2"),
  JDK_1_3(LanguageLevel.JDK_1_3, "1.3"),
  JDK_1_4(LanguageLevel.JDK_1_4, "1.4"),
  JDK_1_5(LanguageLevel.JDK_1_5, "1.5"),
  JDK_1_6(LanguageLevel.JDK_1_6, "1.6"),
  JDK_1_7(LanguageLevel.JDK_1_7, "1.7"),
  JDK_1_8(LanguageLevel.JDK_1_8, "1.8"),
  JDK_1_9(LanguageLevel.JDK_1_9, "1.9");

  private static final JavaSdkVersion MAX_JDK = JDK_1_9;

  private final LanguageLevel myMaxLanguageLevel;
  private final String myDescription;

  JavaSdkVersion(@NotNull LanguageLevel maxLanguageLevel, @NotNull String description) {
    myMaxLanguageLevel = maxLanguageLevel;
    myDescription = description;
  }

  @NotNull
  public LanguageLevel getMaxLanguageLevel() {
    return myMaxLanguageLevel;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  public boolean isAtLeast(@NotNull JavaSdkVersion version) {
    return compareTo(version) >= 0;
  }

  @Override
  public String toString() {
    return super.toString() + ", description: " + myDescription;
  }

  @NotNull
  public static JavaSdkVersion byDescription(@NotNull String description) throws IllegalArgumentException {
    for (JavaSdkVersion version : values()) {
      if (version.getDescription().equals(description)) {
        return version;
      }
    }
    throw new IllegalArgumentException(
      String.format("Can't map Java SDK by description (%s). Available values: %s", description, Arrays.toString(values()))
    );
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
}
