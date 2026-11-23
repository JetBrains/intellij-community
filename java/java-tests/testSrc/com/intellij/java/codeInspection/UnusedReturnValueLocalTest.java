// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue;
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValueLocalInspection;
import com.intellij.psi.util.AccessModifier;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public class UnusedReturnValueLocalTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection");

  private final UnusedReturnValue myGlobal = new UnusedReturnValue();
  private final UnusedReturnValueLocalInspection myTool = new UnusedReturnValueLocalInspection(myGlobal);

  private void doTest() {
    javaInspection.get().doTest("unusedReturnValue/" + javaInspection.get().getTestName(true), myTool);
  }

  @Test
  public void testNonLiteral() {
    doTest();
  }

  @Test
  public void testHierarchy() {
    doTest();
  }

  @Test
  public void testMethodReference() {
    doTest();
  }

  @Test
  public void testSimpleSetter() {
    try {
      myGlobal.IGNORE_BUILDER_PATTERN = true;
      doTest();
    }
    finally {
      myGlobal.IGNORE_BUILDER_PATTERN = false;
    }
  }

  @Test
  public void testVisibilitySetting() {
    try {
      myGlobal.highestModifier = AccessModifier.PRIVATE;
      doTest();
    }
    finally {
      myGlobal.highestModifier = UnusedReturnValue.DEFAULT_HIGHEST_MODIFIER;
    }
  }

  @Test
  public void testChainMethods() {
    try {
      myGlobal.IGNORE_BUILDER_PATTERN = true;
      doTest();
    }
    finally {
      myGlobal.IGNORE_BUILDER_PATTERN = false;
    }
  }

  @Test
  public void testUsedFromGroovy() {
    doTest();
  }
}
