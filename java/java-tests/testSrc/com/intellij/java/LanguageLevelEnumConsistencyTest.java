// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java;

import com.intellij.util.lang.JavaVersion;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

/**
 * For whatever reason, there are two Java language level enums in the project - {@link com.intellij.pom.java.LanguageLevel} and
 * {@link org.jetbrains.jps.model.java.LanguageLevel}. The test ensures their consistency.
 */
public class LanguageLevelEnumConsistencyTest {
  @Test
  public void constants() {
    List<String> ideConstants = Stream.of(com.intellij.pom.java.LanguageLevel.values()).map(Enum::name).collect(Collectors.toList());
    List<String> jpsConstants = Stream.of(org.jetbrains.jps.model.java.LanguageLevel.values()).map(Enum::name).collect(Collectors.toList());
    assertEquals(ideConstants, jpsConstants);
  }

  @Test
  public void highest() {
    JavaVersion ideHighest = com.intellij.pom.java.LanguageLevel.HIGHEST.toJavaVersion();
    JavaVersion jpsHighest = org.jetbrains.jps.model.java.LanguageLevel.HIGHEST.toJavaVersion();
    assertEquals(ideHighest, jpsHighest);
  }
}