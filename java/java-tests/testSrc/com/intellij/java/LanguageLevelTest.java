// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java;

import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public final class LanguageLevelTest {
  @Test
  public void unsupported() {
    @SuppressWarnings("UsagesOfObsoleteApi") 
    LanguageLevel jdk17Preview = LanguageLevel.JDK_17_PREVIEW;
    assertTrue(jdk17Preview.isUnsupported());
    assertTrue(JavaFeature.PATTERNS_IN_SWITCH.isSufficient(jdk17Preview));
    assertFalse(JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS.isSufficient(jdk17Preview));
    assertEquals(LanguageLevel.JDK_17, jdk17Preview.getNonPreviewLevel());
    assertFalse(JavaFeature.PATTERNS_IN_SWITCH.isSufficient(jdk17Preview.getNonPreviewLevel()));
  }

  @Test
  public void getShortText() {
    LanguageLevel jdk17 = LanguageLevel.JDK_17;
    assertEquals("17", jdk17.getShortText());
    //noinspection UsagesOfObsoleteApi
    LanguageLevel jdk17Preview = LanguageLevel.JDK_17_PREVIEW;
    assertEquals("17-preview", jdk17Preview.getShortText());
    LanguageLevel jdk14 = LanguageLevel.JDK_1_4;
    assertEquals("1.4", jdk14.getShortText());
    LanguageLevel jdkX = LanguageLevel.JDK_X;
    assertEquals("X", jdkX.getShortText());
  }
}
