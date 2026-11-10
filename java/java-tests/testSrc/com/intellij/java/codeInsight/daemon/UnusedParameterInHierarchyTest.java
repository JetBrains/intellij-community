// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class UnusedParameterInHierarchyTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_20);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private UnusedDeclarationInspection inspection;

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/unusedParameterInHierarchy");
    inspection = new UnusedDeclarationInspection();
    myFixture.enableInspections(inspection);
  }

  @Test
  public void testExcludingHierarchyInAnonymousClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    inspection.getSharedLocalInspectionTool().setCheckParameterExcludingHierarchy(true);
    doTest();
    assertNotNull(myFixture.getAvailableIntention("Rename 'p' to 'ignoredP'"));
    assertNotNull(myFixture.getAvailableIntention("Do not highlight parameters for inherited methods"));
  }

  @Test
  public void testExcludingHierarchyInLambda() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    inspection.getSharedLocalInspectionTool().setCheckParameterExcludingHierarchy(true);
    doTest();
    assertNotNull(myFixture.getAvailableIntention("Rename 'p' to 'ignoredP'"));
    assertNotNull(myFixture.getAvailableIntention("Do not highlight parameters for inherited methods"));
  }

  @Test
  public void testExcludingHierarchyInOverriddenMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    inspection.getSharedLocalInspectionTool().setCheckParameterExcludingHierarchy(true);
    doTest();
    assertNotNull(myFixture.getAvailableIntention("Rename 'p' to 'ignoredP'"));
    assertNotNull(myFixture.getAvailableIntention("Do not highlight parameters for inherited methods"));
  }

  @Test
  public void testExcludingHierarchyInPlainMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    inspection.getSharedLocalInspectionTool().setCheckParameterExcludingHierarchy(true);
    doTest();
    assertNotNull(myFixture.getAvailableIntention("Rename 'p' to 'ignoredP'"));
    assertNull(myFixture.getAvailableIntention("Do not highlight parameters for inherited methods"));
  }

  @Test
  public void testIncludingHierarchy() {
    inspection.getSharedLocalInspectionTool().setCheckParameterExcludingHierarchy(false);
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}
