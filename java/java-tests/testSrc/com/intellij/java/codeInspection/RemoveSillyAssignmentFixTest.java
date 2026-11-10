// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class RemoveSillyAssignmentFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInspection/removeSillyAssignmentFix");
  }

  @Test
  public void testRemoveCompleteAssignment() {
    doTest();
  }
  @Test
  public void testOperatorAssignment() {
    doTest();
  }
  @Test
  public void testKeepReference() {
    doTest();
  }
  @Test
  public void testFieldAssignsItself() {
    doTest();
  }
  @Test
  public void testFieldKeepInitializer() {
    doTest();
  }
  @Test
  public void testSillyButIncomplete() {
    doTest();
  }
  @Test
  public void testArrayElement1() {
    doTest();
  }
  @Test
  public void testArrayElement2() {
    doTest();
  }

  @Test
  public void testFinalField() { assertQuickfixNotAvailable(); }
  @Test
  public void testFinalField2() { assertQuickfixNotAvailable(); }

  public void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(SillyAssignmentInspection.class);
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    final IntentionAction intention = myFixture.findSingleIntention(InspectionsBundle.message("assignment.to.itself.quickfix.name"));
    assertNotNull(intention);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(intention);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile(myFixture.getTestName(false) + ".after.java");
    });
  }

  protected void assertQuickfixNotAvailable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(SillyAssignmentInspection.class);
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    final String quickfixName = InspectionsBundle.message("assignment.to.itself.quickfix.name");
    assertThat(myFixture.filterAvailableIntentions(quickfixName)).as("Quickfix '" + quickfixName + "' is available unexpectedly").isNullOrEmpty();
  }
}
