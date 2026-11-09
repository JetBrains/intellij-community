// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.security;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class DesignForExtensionInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new DesignForExtensionInspection());

  @Test
  public void testDesignForExtension() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    inspectionFixture.get().doTest();
    final IntentionAction intention = myFixture.getAvailableIntention("Make method 'equals()' 'final'");
    assertNotNull(intention);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(intention);
      myFixture.checkResultByFile(inspectionFixture.get().getTestName(false) + ".after.java");
    });
  }
}