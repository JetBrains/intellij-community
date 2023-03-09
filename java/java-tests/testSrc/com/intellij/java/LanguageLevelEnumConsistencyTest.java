// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.jps.model.java.LanguageLevel;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * For whatever reason, there are two Java language level enums in the project - {@link com.intellij.pom.java.LanguageLevel} and
 * {@link org.jetbrains.jps.model.java.LanguageLevel}. The test ensures their consistency.
 */
public class LanguageLevelEnumConsistencyTest {
  @Test
  public void constants() {
    List<String> ideConstants = ContainerUtil.map(com.intellij.pom.java.LanguageLevel.values(), Enum::name);
    List<String> jpsConstants = ContainerUtil.map(LanguageLevel.values(), Enum::name);
    assertEquals(ideConstants, jpsConstants);
  }

  @Test
  public void highest() {
    JavaVersion ideHighest = com.intellij.pom.java.LanguageLevel.HIGHEST.toJavaVersion();
    JavaVersion jpsHighest = org.jetbrains.jps.model.java.LanguageLevel.HIGHEST.toJavaVersion();
    assertEquals(ideHighest, jpsHighest);
  }
}