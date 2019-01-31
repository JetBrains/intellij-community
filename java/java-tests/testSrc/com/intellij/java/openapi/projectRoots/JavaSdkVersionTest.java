// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.openapi.projectRoots;

import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.jps.model.java.JdkVersionDetector;
import org.junit.Test;

import static org.junit.Assert.*;

public class JavaSdkVersionTest {
  @Test
  public void sdkVersionFromLanguageLevel() {
    assertEquals(JavaSdkVersion.JDK_1_3, JavaSdkVersion.fromLanguageLevel(LanguageLevel.JDK_1_3));
    assertEquals(JavaSdkVersion.JDK_1_6, JavaSdkVersion.fromLanguageLevel(LanguageLevel.JDK_1_6));
    assertEquals(JavaSdkVersion.JDK_1_8, JavaSdkVersion.fromLanguageLevel(LanguageLevel.JDK_1_8));
    assertEquals(JavaSdkVersion.JDK_11, JavaSdkVersion.fromLanguageLevel(LanguageLevel.JDK_11));
    //assertEquals(JavaSdkVersion.JDK_11, JavaSdkVersion.fromLanguageLevel(LanguageLevel.JDK_11_PREVIEW));
  }

  @Test
  public void maxLanguageLevelSanity() {
    for (JavaSdkVersion version : JavaSdkVersion.values()) {
      assertFalse("Fails for " + version.toString(), version.getMaxLanguageLevel().isPreview());
    }
  }

  @Test
  public void sdkVersionFromVersionString() {
    assertEquals(JavaSdkVersion.JDK_1_8, JavaSdkVersion.fromVersionString("1.8.0_131"));
    assertEquals(JavaSdkVersion.JDK_1_9, JavaSdkVersion.fromVersionString("9"));
    assertEquals(JavaSdkVersion.JDK_1_9, JavaSdkVersion.fromVersionString("9-ea"));
    assertEquals(JavaSdkVersion.JDK_1_9, JavaSdkVersion.fromVersionString("9.1.2"));

    assertEquals(JavaSdkVersion.JDK_1_8, JavaSdkVersion.fromVersionString("java version \"1.8.0_131\""));
    assertEquals(JavaSdkVersion.JDK_1_9, JavaSdkVersion.fromVersionString("java version \"9\""));
    assertEquals(JavaSdkVersion.JDK_1_9, JavaSdkVersion.fromVersionString("java version \"9-ea\""));
    assertEquals(JavaSdkVersion.JDK_1_9, JavaSdkVersion.fromVersionString("java version \"9.1.2\""));
  }

  @Test
  public void versionStringDetectorSanity() {
    assertTrue(JdkVersionDetector.isVersionString("java version \"9\""));
    assertFalse(JdkVersionDetector.isVersionString("java version \"\""));

    assertTrue(JdkVersionDetector.isVersionString(JdkVersionDetector.formatVersionString(JavaVersion.compose(9))));
  }
}