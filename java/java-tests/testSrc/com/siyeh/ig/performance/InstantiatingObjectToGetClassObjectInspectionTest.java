// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
@SuppressWarnings("ALL")
public class InstantiatingObjectToGetClassObjectInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new InstantiatingObjectToGetClassObjectInspection());

  @Test
  public void testComplicated() {
    inspectionFixture.get().doTest("import java.util.*;" +
           "class X {" +
           "  void m() {" +
           "    Class<? extends ArrayList> aClass = /*Instantiating object to get Class object*/(new ArrayList</* 1*/String>())/*_*/./* 2*/getClass()/**/;" +
           "  }" +
           "}");
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("instantiating.object.to.get.class.object.replace.quickfix"),
                  "import java.util.*;" +
                  "class X {" +
                  "  void m() {" +
                  "    /* 1*/" +
                  "    /* 2*/ Class<? extends ArrayList> aClass = ArrayList.class;" +
                  "  }" +
                  "}");
  }

  @Test
  public void testTopLevelExpresion() {
    inspectionFixture.get().doTest("class X {" +
           "  void m() {" +
           "    /*Instantiating object to get Class object*/new /*_*/String().getClass()/**/;" +
           "  }" +
           "}");
    inspectionFixture.get().assertQuickFixNotAvailable(InspectionGadgetsBundle.message("instantiating.object.to.get.class.object.replace.quickfix"));
  }

  @Test
  public void testAnonymousClass() {
    inspectionFixture.get().doTest("class X {" +
           "  void m() {" +
           "    new Object() {}.getClass();" +
           "  }" +
           "}");
  }

  @Test
  public void testArray() {
    inspectionFixture.get().doTest("class X {" +
           "  void m() {" +
           "    Class<? extends String[]> aClass = /*Instantiating object to get Class object*/new String[]/*_*/ {}.getClass()/**/;" +
           "  }" +
           "}");
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("instantiating.object.to.get.class.object.replace.quickfix"),
                  "class X {" +
                  "  void m() {" +
                  "    Class<? extends String[]> aClass = String[].class;" +
                  "  }" +
                  "}");
  }

  @Test
  public void testLocalClasses() {
    inspectionFixture.get().doTest("class X {" +
           "  void m() {" +
           "    class A {" +
           "      class B {" +
           "      }" +
           "    }" +
           "    Class<?> clazz = /*Instantiating object to get Class object*/new A()/*_*/.new B().getClass()/**/;" +
           "  }" +
           "}");
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("instantiating.object.to.get.class.object.replace.quickfix"),
                  "class X {" +
                  "  void m() {" +
                  "    class A {" +
                  "      class B {" +
                  "      }" +
                  "    }" +
                  "    Class<?> clazz = A.B.class;" +
                  "  }" +
                  "}");
  }
}