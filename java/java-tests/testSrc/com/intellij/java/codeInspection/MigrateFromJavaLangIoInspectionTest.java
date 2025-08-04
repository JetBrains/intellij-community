// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.MigrateFromJavaLangIoInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
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

  private void doTest(String message) {
    myFixture.enableInspections(new MigrateFromJavaLangIoInspection());
    myFixture.testHighlighting(true, true, true, "before" + getTestName(false) + ".java");
    myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(message));
    myFixture.checkResultByFile("after" + getTestName(false) + ".java");
  }
}