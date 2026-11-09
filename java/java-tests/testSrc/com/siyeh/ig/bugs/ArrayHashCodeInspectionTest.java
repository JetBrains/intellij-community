// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class ArrayHashCodeInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ArrayHashCodeInspection(), getEnvironmentClasses());

  @Test
  public void testArrayHashCode() {
    inspectionFixture.get().doTest();
  }

  private static String[] getEnvironmentClasses() {
    return new String[]{
      "package java.util;" +
      "public class Objects {" +
      "    public static int hash(Object... values) {" +
      "        return Arrays.hashCode(values);" +
      "    }" +
      "}"
    };
  }
}
