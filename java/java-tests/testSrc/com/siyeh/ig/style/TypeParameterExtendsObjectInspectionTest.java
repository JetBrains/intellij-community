// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.java.codeInsight.JSpecifyTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class TypeParameterExtendsObjectInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @Test
  public void testTypeParameterExtendsObject() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final TypeParameterExtendsObjectInspection inspection = getTypeParameterExtendsObjectInspection(false);
    myFixture.enableInspections(inspection);

    inspectionFixture.get().doTest();
  }

  @Test
  public void testTypeParameterExtendsObjectWithContainerAnnotation() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final TypeParameterExtendsObjectInspection inspection = getTypeParameterExtendsObjectInspection(true);
    myFixture.enableInspections(inspection);
    JSpecifyTestUtil.addJSpecifyNullMarked(myFixture);

    inspectionFixture.get().doTest();
  }

  @Test
  public void testTypeParameterExtendsObjectIgnoreAnnotated() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final TypeParameterExtendsObjectInspection inspection = getTypeParameterExtendsObjectInspection(true);
    myFixture.enableInspections(inspection);

    inspectionFixture.get().doTest();
  }

  private TypeParameterExtendsObjectInspection getTypeParameterExtendsObjectInspection(boolean ignoreAnnotatedObject) {
    final TypeParameterExtendsObjectInspection inspection = new TypeParameterExtendsObjectInspection();
    inspection.ignoreAnnotatedObject = ignoreAnnotatedObject;
    return inspection;
  }

  @Nullable
  private InspectionProfileEntry getInspection() {
    return getTypeParameterExtendsObjectInspection(false);
  }
}