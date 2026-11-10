// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.JavaTestUtil;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class UnnecessaryParenthesesInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_15);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/style/unnecessary_parentheses");
  }

  @Test
  public void testUnnecessaryParenthesesInspection() {
    UnnecessaryParenthesesInspection inspection = new UnnecessaryParenthesesInspection();
    inspection.ignoreParenthesesOnConditionals = true;
    doTest(inspection);
  }

  private void doTest(UnnecessaryParenthesesInspection inspection) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(inspection);
    ProjectInspectionProfileManager.getInstance(myFixture.getProject()).getCurrentProfile()
      .setErrorLevel(HighlightDisplayKey.find(inspection.getShortName()), HighlightDisplayLevel.WARNING, myFixture.getProject());
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testClarifyingParentheses() {
    UnnecessaryParenthesesInspection inspection = new UnnecessaryParenthesesInspection();
    inspection.ignoreClarifyingParentheses = true;
    doTest(inspection);
  }

}
