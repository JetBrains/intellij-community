// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java;

import com.intellij.pom.java.LanguageLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class LanguageLevelTest {
  @Test
  public void unsupported() {
    @SuppressWarnings("UsagesOfObsoleteApi") 
    LanguageLevel jdk17Preview = LanguageLevel.JDK_17_PREVIEW;
    assertTrue(jdk17Preview.isUnsupported());
    assertEquals(LanguageLevel.JDK_20_PREVIEW, jdk17Preview.getSupportedLevel());
    assertEquals(LanguageLevel.JDK_17, jdk17Preview.getNonPreviewLevel());
  }
}
