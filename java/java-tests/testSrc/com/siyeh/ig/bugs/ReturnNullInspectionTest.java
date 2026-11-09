// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixtureByTestName;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ReturnNullInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixtureByTestName(projectFixture, testName -> getInspection(testName), getEnvironmentClasses());

  @Test
  public void testReturnNull() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final NullableNotNullManager nnnManager = NullableNotNullManager.getInstance(myFixture.getProject());
    nnnManager.setNullables("com.siyeh.igtest.bugs.Nullable");
    Disposer.register(myFixture.getTestRootDisposable(), nnnManager::setNullables);
    inspectionFixture.get().doTest();
  }

  @Test
  public void testWarnOptional() {
    inspectionFixture.get().doTest();
  }

  @Nullable
  private static InspectionProfileEntry getInspection(String testName) {
    final ReturnNullInspection inspection = new ReturnNullInspection();
    inspection.m_reportObjectMethods = !"WarnOptional".equals(testName);
    inspection.m_ignorePrivateMethods = "WarnOptional".equals(testName);
    return inspection;
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
package com.siyeh.igtest.bugs;
import java.lang.annotation.*;
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE_USE, ElementType.METHOD})
public @interface Nullable {}"""
    };
  }
}