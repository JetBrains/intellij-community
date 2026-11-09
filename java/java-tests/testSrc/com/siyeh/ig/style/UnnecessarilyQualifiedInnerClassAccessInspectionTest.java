// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class UnnecessarilyQualifiedInnerClassAccessInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new UnnecessarilyQualifiedInnerClassAccessInspection());

  @Test
  public void testRemoveQualifier() {
    inspectionFixture.get().doTest("""
             class X {
               /*'Y' is unnecessarily qualified with 'X'*//*_*/X/**//* 1*/./* 2*/Y foo;
              \s
               class Y{}
             }""");
    inspectionFixture.get().checkQuickFix("Remove qualifier", """
      class X {
        /* 2*//* 1*/ Y foo;
       \s
        class Y{}
      }""");
  }

  @Test
  public void testRemoveQualifierWithImport() {
    inspectionFixture.get().doTest("""
             package p;
             import java.util.List;
             abstract class X implements List</*'Y' is unnecessarily qualified with 'X'*//*_*/X/**/.Y> {
               class Y{}
             }""");
    inspectionFixture.get().checkQuickFix("Remove qualifier", """
      package p;
      import p.X.Y;

      import java.util.List;
      abstract class X implements List<Y> {
        class Y{}
      }"""
    );
  }

  @Test
  public void testUnnecessarilyQualifiedInnerClassAccess() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }

  @Test
  public void testNoImports() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final UnnecessarilyQualifiedInnerClassAccessInspection inspection = new UnnecessarilyQualifiedInnerClassAccessInspection();
    inspection.ignoreReferencesNeedingImport = true;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }
}
