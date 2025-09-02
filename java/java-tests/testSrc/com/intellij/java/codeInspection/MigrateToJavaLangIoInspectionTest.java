// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.MigrateToJavaLangIoInspection;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class MigrateToJavaLangIoInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/migrateToJavaLangIo/";
  }

  public void testPrintlnImplicitClass() { doTest("Replace with 'IO.println()'"); }

  public void testPrintRegularClass() { doTest("Replace with 'IO.print()'"); }

  public void testPrintArrayChar() {
    doNotFind(InspectionsBundle.message("fix.all.inspection.problems.in.file", JavaBundle.message("inspection.migrate.to.java.lang.io.name")));
  }

  public void testPrintf() {
    doNotFind(InspectionsBundle.message("fix.all.inspection.problems.in.file", JavaBundle.message("inspection.migrate.to.java.lang.io.name")));
  }

  public void testPrintStream() {
    doNotFind(InspectionsBundle.message("fix.all.inspection.problems.in.file", JavaBundle.message("inspection.migrate.to.java.lang.io.name")));
  }

  private void doNotFind(String message) {
    addIOClass(myFixture);
    MigrateToJavaLangIoInspection inspection = new MigrateToJavaLangIoInspection();
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, true, true, "before" + getTestName(false) + ".java");
    IntentionAction intention = myFixture.getAvailableIntention(message);
    assertNull(intention);
  }

  private void doTest(String message) {
    addIOClass(myFixture);
    myFixture.enableInspections(new MigrateToJavaLangIoInspection());
    myFixture.testHighlighting(true, true, true, "before" + getTestName(false) + ".java");
    myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(message));
    myFixture.checkResultByFile("after" + getTestName(false) + ".java");
  }

  public static void addIOClass(@NotNull JavaCodeInsightTestFixture fixture) {
    fixture.addClass("""
                         package java.lang;
                         public final class IO {
                           public static void println(Object obj) {}
                           public static void println() {}
                           public static void print(Object obj) {}
                         }
                         """);
  }
}