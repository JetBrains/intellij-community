/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

/**
 * Represents version of Java SDK. Use {@link JavaSdk#getVersion(Sdk)} method to obtain version of an {@link Sdk}
 *
 * @author nik
 */
public enum JavaSdkVersion {
  JDK_1_0(LanguageLevel.JDK_1_3), JDK_1_1(LanguageLevel.JDK_1_3), JDK_1_2(LanguageLevel.JDK_1_3), JDK_1_3(LanguageLevel.JDK_1_3),
  JDK_1_4(LanguageLevel.JDK_1_4),
  JDK_1_5(LanguageLevel.JDK_1_5),
  JDK_1_6(LanguageLevel.JDK_1_6),
  JDK_1_7(LanguageLevel.JDK_1_7),
  JDK_1_8(LanguageLevel.JDK_1_8);
  private final LanguageLevel myMaxLanguageLevel;

  JavaSdkVersion(@NotNull LanguageLevel maxLanguageLevel) {
    myMaxLanguageLevel = maxLanguageLevel;
  }

  @NotNull
  public LanguageLevel getMaxLanguageLevel() {
    return myMaxLanguageLevel;
  }

  public boolean isAtLeast(@NotNull JavaSdkVersion version) {
    return compareTo(version) >= 0;
  }
}
