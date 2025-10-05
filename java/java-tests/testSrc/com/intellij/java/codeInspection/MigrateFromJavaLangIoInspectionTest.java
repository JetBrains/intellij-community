// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.MigrateFromJavaLangIoInspection;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class MigrateFromJavaLangIoInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/migrateFromJavaLangIo/";
  }

  public void testPrintlnResolved() {
    MigrateToJavaLangIoInspectionTest.addIOClass(myFixture);
    doTest("Replace with 'System.out.println()'");
  }

  public void testPrintUnresolved() { doTest("Replace with 'System.out.print()'"); }

  public void testPrintArrayChar() {
    doNotFind(getFixAllMessage());
  }

  private static @Nls @NotNull String getFixAllMessage() {
    return InspectionsBundle.message("fix.all.inspection.problems.in.file",
                                     JavaBundle.message("inspection.migrate.from.java.lang.io.name"));
  }

  public void testPartialQualifier() {
    doNotFind(
      getFixAllMessage());
  }

  private void doNotFind(String message) {
    MigrateToJavaLangIoInspectionTest.addIOClass(myFixture);
    MigrateFromJavaLangIoInspection inspection = new MigrateFromJavaLangIoInspection();
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, true, true, "before" + getTestName(false) + ".java");
    IntentionAction intention = myFixture.getAvailableIntention(message);
    assertNull(intention);
  }

  private void doTest(String message) {
    myFixture.enableInspections(new MigrateFromJavaLangIoInspection());
    myFixture.testHighlighting(true, true, true, "before" + getTestName(false) + ".java");
    myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(message));
    myFixture.checkResultByFile("after" + getTestName(false) + ".java");
  }
}