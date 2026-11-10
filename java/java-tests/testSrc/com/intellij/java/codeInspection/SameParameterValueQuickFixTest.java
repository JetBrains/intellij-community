// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public final class SameParameterValueQuickFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/sameParameterValueQuickFix");
  }

  @Test
  public void testSimple() {
    doTest();
  }
  @Test
  public void testCastedValue() {
    doTest();
  }
  @Test
  public void testStringThatNeedsEscaping() {
    doTest(false, false);
  }
  @Test
  public void testLongValue() {
    doTest();
  }
  @Test
  public void testVararg() {
    doTest();
  }

  private void doTest() {
    doTest(true, false);
  }

  private void doTest(boolean testHighlighting, boolean quickFixNotAvailable) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    SameParameterValueInspection globalInspection = new SameParameterValueInspection();
    globalInspection.ignoreWhenRefactoringIsComplicated = false;
    LocalInspectionTool inspection = globalInspection.getSharedLocalInspectionTool();
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(name + ".java");
    if (testHighlighting) myFixture.testHighlighting(true, false, false);
    int caretOffset = ReadAction.computeBlocking(() -> myFixture.getCaretOffset());
    assertTrue(caretOffset != 0, "No <caret> found in '" + name + ".java'");
    final @NotNull List<IntentionAction> intentions = myFixture.filterAvailableIntentions("Inline value");
    if (quickFixNotAvailable) {
      assertThat(intentions).as("intention found when it should not be available").isNullOrEmpty();
    }
    else {
      assertEquals(1, intentions.size(), "intention not found");
      EdtTestUtil.runInEdtAndWait(() -> {
        myFixture.checkPreviewAndLaunchAction(intentions.get(0));
        myFixture.checkResultByFile(name + ".after.java");
      });
    }
  }
}
