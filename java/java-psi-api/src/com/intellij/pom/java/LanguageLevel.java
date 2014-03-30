/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dsl
 */
public enum LanguageLevel {
  JDK_1_3(JavaCoreBundle.message("jdk.1.3.language.level.description")),
  JDK_1_4(JavaCoreBundle.message("jdk.1.4.language.level.description")),
  JDK_1_5(JavaCoreBundle.message("jdk.1.5.language.level.description")),
  JDK_1_6(JavaCoreBundle.message("jdk.1.6.language.level.description")),
  JDK_1_7(JavaCoreBundle.message("jdk.1.7.language.level.description")),
  JDK_1_8(JavaCoreBundle.message("jdk.1.8.language.level.description"));

  public static final LanguageLevel HIGHEST = JDK_1_8;
  public static final Key<LanguageLevel> KEY = Key.create("LANGUAGE_LEVEL");

  private final String myPresentableText;

  LanguageLevel(@NotNull @Nls String presentableText) {
    myPresentableText = presentableText;
  }

  @NotNull
  @Nls
  public String getPresentableText() {
    return myPresentableText;
  }

  public boolean isAtLeast(@NotNull LanguageLevel level) {
    return compareTo(level) >= 0;
  }

  @Nullable
  public static LanguageLevel parse(@Nullable String value) {
    if ("1.3".equals(value)) return JDK_1_3;
    if ("1.4".equals(value)) return JDK_1_4;
    if ("1.5".equals(value)) return JDK_1_5;
    if ("1.6".equals(value)) return JDK_1_6;
    if ("1.7".equals(value)) return JDK_1_7;
    if ("1.8".equals(value)) return JDK_1_8;

    return null;
  }
}
