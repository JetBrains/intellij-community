// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class EqualsReplaceableByObjectsCallInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  private EqualsReplaceableByObjectsCallInspection myInspection = new EqualsReplaceableByObjectsCallInspection();

  @AfterEach
  void tearDown() {
    myInspection = null;
  }

  @Test
  public void testEqualsReplaceableByObjectsCall() {
    testEqualsReplaceable(false);
  }

  @Test
  public void testEqualsReplaceableByObjectsCallCheckNull() {
    testEqualsReplaceable(true);
  }

  protected void testEqualsReplaceable(boolean checkNotNull) {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myInspection.checkNotNull = checkNotNull;
    myFixture.configureByFile(inspectionFixture.get().getTestName(false) + ".java");
    myFixture.testHighlighting(true, true, false);
  }

  @Nullable
  private LocalInspectionTool getInspection() {
    return myInspection;
  }
}