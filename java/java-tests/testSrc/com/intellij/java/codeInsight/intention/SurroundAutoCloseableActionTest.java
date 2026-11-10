// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.java.JavaBundle;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight/surroundAutoCloseable/")
@TestApplication
public class SurroundAutoCloseableActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/surroundAutoCloseable/");
  }

  @Test
  public void testSimple() {
    doTest();
  }
  @Test
  public void testSimplePast() {
    doTest();
  }
  @Test
  public void testUsage() {
    doTest();
  }
  @Test
  public void testMixedUsages() {
    doTest();
  }
  @Test
  public void testLastDeclaration() {
    doTest();
  }
  @Test
  public void testSplitVar() {
    doTest();
  }
  @Test
  public void testExpression() {
    doTest();
  }
  @Test
  public void testExpressionIncomplete() {
    doTest();
  }
  @Test
  public void testUnrelatedVariable() {
    doTest();
  }
  @Test
  public void testCommentsInVarDeclaration() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings.getInstance(myFixture.getProject()).GENERATE_FINAL_LOCALS = true;
    doTest();
  }
  @Test
  public void testImplicitlyTypedDeclaration() {
    doTest();
  }

  @Test
  public void testNullTypeMovedVariable() {
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    String intention = JavaBundle.message("intention.surround.resource.with.ARM.block");
    EdtTestUtil.runInEdtAndWait(() -> CodeInsightTestUtil.doIntentionTest(myFixture, intention, name + ".java", name + "_after.java"));
  }
}