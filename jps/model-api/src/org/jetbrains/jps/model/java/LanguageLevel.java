// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java;

import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;

/**
 * {@code com.intellij.pom.java.LanguageLevel} is an IDE-side counterpart of this enum.
 *
 * @author nik
 */
public enum LanguageLevel {
  JDK_1_3(3), JDK_1_4(4), JDK_1_5(5), JDK_1_6(6), JDK_1_7(7), JDK_1_8(8), JDK_1_9(9), JDK_10(10), JDK_11(11), JDK_X(12);

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

  /** @deprecated use {@link JpsJavaSdkType#complianceOption} (to be removed in IDEA 2019) */
  public String getComplianceOption() {
    return JpsJavaSdkType.complianceOption(toJavaVersion());
  }
}