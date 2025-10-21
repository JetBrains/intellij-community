// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.MigrateFromJavaLangIoInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class MigrateFromJavaLangIoInspection21Test extends LightJavaCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/migrateFromJavaLangIo/";
  }

  public void testPrintUnresolved() {
    myFixture.enableInspections(new MigrateFromJavaLangIoInspection());
    myFixture.testHighlighting(true, true, true, "before" + getTestName(false) + ".java");
    myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention("Replace with 'System.out.print()'"));
    myFixture.checkResultByFile("after" + getTestName(false) + ".java");
  }
}