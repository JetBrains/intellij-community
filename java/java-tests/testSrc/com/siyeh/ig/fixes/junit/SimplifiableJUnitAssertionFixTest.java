// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.junit;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import com.siyeh.ig.testFrameworks.SimplifiableAssertionInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class SimplifiableJUnitAssertionFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_18);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new SimplifiableAssertionInspection());

  @Test
  public void testJUnit3TestCase() { doQuickFixTest(); }

  @Test
  public void testJUnit4TestCase() { doQuickFixTest(); }

  @Test
  public void testIntegerPrimitive() { doQuickFixTest(); }

  @Test
  public void testBoxedComparisonToEquals() { doQuickFixTest(); }

  @Test
  public void testBoxedComparisonToEquals1() { doQuickFixTest(); }

  @Test
  public void testDoublePrimitive() { doQuickFixTest(); }

  @Test
  public void testFloatPrimitive() { doQuickFixTest(); }

  @Test
  public void testEqualsToTrueJUnit5() { doQuickFixTest(); }

  @Test
  public void testTrueToEqualsJUnit5() { doQuickFixTest(); }

  @Test
  public void testTrueToEqualsJUnit5Order() { doQuickFixTest(); }

  @Test
  public void testTrueToEqualsBetweenIncompatibleTypes() { doQuickFixTest(); }

  @Test
  public void testFalseToNotEqualsJUnit4() { doQuickFixTest(); }

  @Test
  public void testObjectEqualsToEquals() { doQuickFixTest(); }

  @Test
  public void testTrueToArrayEquals() { doQuickFixTest(); }

  @Test
  public void testTrueToFloatArrayEquals() { doQuickFixTest(); }

  @Test
  public void testTrueToArrayEqualsJUnit5() { doQuickFixTest(); }

  @Test
  public void testNegatedTrue() { doQuickFixTest(); }

  @Test
  public void testSimplifiableInstanceOf() { doQuickFixTest(); }

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    inspectionFixture.get().getFixture().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igfixes/junit/simplifiable_junit_assertion");

    ModuleRootModificationUtil.updateModel(myFixture.getModule(), model -> {
      MavenDependencyUtil.addFromMaven(model, "org.junit.jupiter:junit-jupiter-api:5.8.1");
      MavenDependencyUtil.addFromMaven(model, "org.junit.jupiter:junit-jupiter-params:5.8.1");
      MavenDependencyUtil.addFromMaven(model, "junit:junit:4.13.2");
      MavenDependencyUtil.addFromMaven(model, "org.testng:testng:7.8.0");
    });
  }

  protected void doQuickFixTest() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }
}