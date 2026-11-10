// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.valuebased;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.valuebased.SynchronizeOnValueBasedClassInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class SynchronizeOnValueBasedClassInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  static final @NonNls String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/valuebased";

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath());
    final String abstractValueBased = BASE_PATH + "/classes/AbstractValueBased.java";
    final String ivalueBased = BASE_PATH + "/classes/IValueBased.java";
    final String openValueBased = BASE_PATH + "/classes/OpenValueBased.java";

    myFixture.configureByFile(abstractValueBased);
    myFixture.configureByFile(ivalueBased);
    myFixture.configureByFile(openValueBased);
    myFixture.enableInspections(new SynchronizeOnValueBasedClassInspection());
  }

  @Test
  public void testExtendValueBasedClass() { doTest(); }
  @Test
  public void testUseValueBasedInstanceAsMonitor() { doTest(); }
  @Test
  public void testImplementValueBasedInterface() { doTest(); }
  @Test
  public void testComplexValueBasedHierarchy() { doTest(); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String filePath = BASE_PATH + "/" + myFixture.getTestName(false) + ".java";
    myFixture.configureByFile(filePath);
    myFixture.checkHighlighting();
  }
}
