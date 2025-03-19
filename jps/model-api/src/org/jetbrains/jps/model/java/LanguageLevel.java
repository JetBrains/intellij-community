// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  JDK_16(16),
  JDK_17(17), JDK_17_PREVIEW(17),
  JDK_18(18), JDK_18_PREVIEW(18),
  JDK_19(19), JDK_19_PREVIEW(19),
  JDK_20(20), JDK_20_PREVIEW(20),
  JDK_21(21), JDK_21_PREVIEW(21),
  JDK_22(22), JDK_22_PREVIEW(22),
  JDK_23(23), JDK_23_PREVIEW(23),
  JDK_24(24), JDK_24_PREVIEW(24),
  JDK_X(24),
  
  ;

  public static final LanguageLevel HIGHEST = JDK_23;

  private final JavaVersion myVersion;

  LanguageLevel(int major) {
    myVersion = JavaVersion.compose(major);
  }

  public @NotNull JavaVersion toJavaVersion() {
    return myVersion;
  }

  /**
   * @return the language level feature number (like 8 for {@link #JDK_1_8}).
   */
  public int feature() {
    return myVersion.feature;
  }

  public boolean isPreview() {
    return name().endsWith("_PREVIEW") || name().endsWith("_X");
  }
}