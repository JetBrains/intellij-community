// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import com.siyeh.ig.resources.IOResourceInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Alexey
 */
@TestApplication
public class IOResourceInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new IOResourceInspection(), getEnvironmentClasses());

  @Test
  public void testIOResource() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testInsideTry() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final IOResourceInspection inspection = new IOResourceInspection();
    inspection.insideTryAllowed = true;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest();
  }

  @Test
  public void testNoEscapingThroughMethodCalls() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final IOResourceInspection inspection = new IOResourceInspection();
    inspection.anyMethodMayClose = false;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest();
  }

  
  private static String[] getEnvironmentClasses() {
    return new String[] {
      "package org.apache.commons.io;" +
      "import java.io.InputStream;" +
      "public class IOUtils {" +
      "  public static void closeQuietly(InputStream input) {}" +
      "}"
    };
  }
}
