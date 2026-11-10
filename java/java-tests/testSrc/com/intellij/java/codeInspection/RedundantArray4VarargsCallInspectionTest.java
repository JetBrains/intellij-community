// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.miscGenerics.RedundantArrayForVarargsCallInspection;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author anna
 */
@TestApplication
public class RedundantArray4VarargsCallInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/redundantArrayForVarargs/quickFix");
    myFixture.enableInspections(new RedundantArrayForVarargsCallInspection());
  }

  @Test
  public void testPreserveComments() {
    doTest();
  }
  @Test
  public void testRemoveTailingCommas() {
    doTest();
  }
  @Test
  public void testParentheses() {
    doTest();
  }
  @Test
  public void testInsertSeparatingComma() {
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    myFixture.configureByFile(name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(myFixture.findSingleIntention(JavaBundle.message("inspection.redundant.array.creation.quickfix")));
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile(name + "_after.java");
    });
  }
}