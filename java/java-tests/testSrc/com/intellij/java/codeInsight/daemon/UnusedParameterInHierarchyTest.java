// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class UnusedParameterInHierarchyTest extends LightJavaCodeInsightFixtureTestCase {
  private UnusedDeclarationInspection inspection;

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/unusedParameterInHierarchy";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    inspection = new UnusedDeclarationInspection();
    myFixture.enableInspections(inspection);
  }

  public void testExcludingHierarchyInAnonymousClass() {
    inspection.getSharedLocalInspectionTool().setCheckParameterExcludingHierarchy(true);
    doTest();
    assertNotNull(myFixture.getAvailableIntention("Rename 'p' to 'ignoredP'"));
    assertNotNull(myFixture.getAvailableIntention("Do not highlight parameters for inherited methods"));
  }

  public void testExcludingHierarchyInLambda() {
    inspection.getSharedLocalInspectionTool().setCheckParameterExcludingHierarchy(true);
    doTest();
    assertNotNull(myFixture.getAvailableIntention("Rename 'p' to 'ignoredP'"));
    assertNotNull(myFixture.getAvailableIntention("Do not highlight parameters for inherited methods"));
  }

  public void testExcludingHierarchyInOverriddenMethod() {
    inspection.getSharedLocalInspectionTool().setCheckParameterExcludingHierarchy(true);
    doTest();
    assertNotNull(myFixture.getAvailableIntention("Rename 'p' to 'ignoredP'"));
    assertNotNull(myFixture.getAvailableIntention("Do not highlight parameters for inherited methods"));
  }

  public void testExcludingHierarchyInPlainMethod() {
    inspection.getSharedLocalInspectionTool().setCheckParameterExcludingHierarchy(true);
    doTest();
    assertNotNull(myFixture.getAvailableIntention("Rename 'p' to 'ignoredP'"));
    assertNull(myFixture.getAvailableIntention("Do not highlight parameters for inherited methods"));
  }

  public void testIncludingHierarchy() {
    inspection.getSharedLocalInspectionTool().setCheckParameterExcludingHierarchy(false);
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}
