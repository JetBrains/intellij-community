// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java;

import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;

/**
 * The {@link com.intellij.pom.java.LanguageLevel} class is an IDE-side counterpart of this enum.
 */
public enum LanguageLevel {
  JDK_1_3(3),
  JDK_1_4(4),
  JDK_1_5(5),
  JDK_1_6(6),
  JDK_1_7(7),
  JDK_1_8(8),
  JDK_1_9(9),
  JDK_10(10),
  JDK_11(11),
  JDK_12(12),
  JDK_13(13),
  JDK_14(14),
  JDK_15(15),
  JDK_16(16), JDK_16_PREVIEW(16),
  JDK_17(17), JDK_17_PREVIEW(17),
  JDK_18(18), JDK_18_PREVIEW(18),
  JDK_X(19);

  public static final LanguageLevel HIGHEST = JDK_17;

  private final JavaVersion myVersion;

  LanguageLevel(int major) {
    myVersion = JavaVersion.compose(major);
  }

  @NotNull
  public JavaVersion toJavaVersion() {
    return myVersion;
  }

  public boolean isPreview() {
    return name().endsWith("_PREVIEW");
  }
}