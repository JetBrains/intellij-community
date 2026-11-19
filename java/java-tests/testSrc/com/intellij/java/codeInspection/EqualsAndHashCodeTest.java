// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.equalsAndHashcode.EqualsAndHashcode;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public class EqualsAndHashCodeTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection");

  
  private final EqualsAndHashcode myTool = new EqualsAndHashcode();

  private void doTest() {
    javaInspection.get().doTest("equalsAndHashcode/" + javaInspection.get().getTestName(true), myTool);
  }

  @Test
  public void testInnerClass() {
    doTest();
  }

  @Test
  public void testHierarchy() {
    doTest();
  }

  @Test
  public void testRecord() {
    doTest();
  }
}
