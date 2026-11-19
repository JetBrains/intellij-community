// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;
import com.intellij.JavaTestUtil;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;


@TestApplication
public class UnnecessarilyQualifiedStaticallyImportedElementInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/ig");


  @Test
  public void testSimple() { doTest(); }
  @Test
  public void testSameMemberNames() { doTest(); }
  @Test
  public void testMethodRef() { doTest(); }
  @Test
  public void testChainedMethodCall() { doTest(); }
  @Test
  public void testOverriding() { doTest(); }
  @Test
  public void testYield() { doTest(); }
  @Test
  public void testInterfacing() { doTest(); }

  private void doTest() {
    javaInspection.get().doTest("com/siyeh/igtest/style/unnecessarily_qualified_statically_imported_element/" + javaInspection.get().getTestName(true),
           new UnnecessarilyQualifiedStaticallyImportedElementInspection());
  }
}