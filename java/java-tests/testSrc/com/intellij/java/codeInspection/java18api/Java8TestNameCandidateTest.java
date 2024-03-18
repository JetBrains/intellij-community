// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.java18api;

import com.intellij.codeInspection.java18api.Java8MapApiInspection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class Java8TestNameCandidateTest {
  private final String expected;
  private final String actual;

  public Java8TestNameCandidateTest(String expected, String actual) {
    this.expected = expected;
    this.actual = actual;
  }

  @Parameterized.Parameters
  public static Object[][] primeNumbers() {
    return new Object[][]{
      {"e", "element"},
      {"t", "accessToken"},
      {"s", "SQL"},
      {"n", "myUserName"},
      {"v", "___VAR"},
      {"k", "_1"},
    };
  }

  @Test
  public void testNameCandidate() {
    assertEquals(expected, Java8MapApiInspection.getNameCandidate(actual));
  }
}
