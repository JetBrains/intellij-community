// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.testFramework.IdeaTestUtil;
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
public class SuppressModuleInfoInspectionsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/suppressModuleInfoInspections");
  }

  @Test
  public void testModuleInfo() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // need to set jdk version here because it is not set correctly in the mock jdk which causes problems in
    // com.intellij.codeInspection.JavaSuppressionUtil#canHave15Suppressions()
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_9, myFixture.getModule(), myFixture.getTestRootDisposable());
    final LocalInspectionTool inspection = new JavaDocReferenceInspection();
    myFixture.enableInspections(inspection);
    myFixture.configureByFile("module-info.java");
    myFixture.testHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("Suppress for module declaration"));
    myFixture.checkResultByFile("module-info.after.java");
  }
}