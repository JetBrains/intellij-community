// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;


public class SuppressModuleInfoInspectionsTest extends LightJavaCodeInsightFixtureTestCase {

  public void testModuleInfo() {
    // need to set jdk version here because it is not set correctly in the mock jdk which causes problems in
    // com.intellij.codeInspection.JavaSuppressionUtil#canHave15Suppressions()
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_9, myFixture.getModule(), getTestRootDisposable());
    final LocalInspectionTool inspection = new JavaDocReferenceInspection();
    myFixture.enableInspections(inspection);
    myFixture.configureByFile("module-info.java");
    myFixture.testHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("Suppress for module declaration"));
    myFixture.checkResultByFile("module-info.after.java");
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/suppressModuleInfoInspections";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }
}