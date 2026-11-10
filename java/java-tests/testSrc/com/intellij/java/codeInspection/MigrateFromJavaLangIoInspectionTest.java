// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.MigrateFromJavaLangIoInspection;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class MigrateFromJavaLangIoInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/migrateFromJavaLangIo/");
  }

  @Test
  public void testPrintlnResolved() {
    doTest("Replace with 'System.out.println()'");
  }

  @Test
  public void testPrintArrayChar() {
    doNotFind(getFixAllMessage());
  }

  private static @Nls @NotNull String getFixAllMessage() {
    return InspectionsBundle.message("fix.all.inspection.problems.in.file",
                                     JavaBundle.message("inspection.migrate.from.java.lang.io.name"));
  }

  @Test
  public void testPartialQualifier() {
    doNotFind(
      getFixAllMessage());
  }

  private void doNotFind(String message) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    MigrateFromJavaLangIoInspection inspection = new MigrateFromJavaLangIoInspection();
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, true, true, "before" + myFixture.getTestName(false) + ".java");
    IntentionAction intention = myFixture.getAvailableIntention(message);
    assertNull(intention);
  }

  private void doTest(String message) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new MigrateFromJavaLangIoInspection());
    myFixture.testHighlighting(true, true, true, "before" + myFixture.getTestName(false) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(message));
      myFixture.checkResultByFile("after" + myFixture.getTestName(false) + ".java");
    });
  }
}