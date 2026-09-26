// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("ALL")
public class InstantiatingObjectToGetClassObjectInspectionTest extends LightJavaInspectionTestCase {

  public void testComplicated() {
    doTest("import java.util.*;" +
           "class X {" +
           "  void m() {" +
           "    Class<? extends ArrayList> aClass = /*Instantiating object to get Class object*/(new ArrayList</* 1*/String>())/*_*/./* 2*/getClass()/**/;" +
           "  }" +
           "}");
    checkQuickFix(InspectionGadgetsBundle.message("instantiating.object.to.get.class.object.replace.quickfix"),
                  "import java.util.*;" +
                  "class X {" +
                  "  void m() {" +
                  "    /* 1*/" +
                  "    /* 2*/ Class<? extends ArrayList> aClass = ArrayList.class;" +
                  "  }" +
                  "}");
  }

  public void testTopLevelExpresion() {
    doTest("class X {" +
           "  void m() {" +
           "    /*Instantiating object to get Class object*/new /*_*/String().getClass()/**/;" +
           "  }" +
           "}");
    assertQuickFixNotAvailable(InspectionGadgetsBundle.message("instantiating.object.to.get.class.object.replace.quickfix"));
  }

  public void testAnonymousClass() {
    doTest("class X {" +
           "  void m() {" +
           "    new Object() {}.getClass();" +
           "  }" +
           "}");
  }

  public void testArray() {
    doTest("class X {" +
           "  void m() {" +
           "    Class<? extends String[]> aClass = /*Instantiating object to get Class object*/new String[]/*_*/ {}.getClass()/**/;" +
           "  }" +
           "}");
    checkQuickFix(InspectionGadgetsBundle.message("instantiating.object.to.get.class.object.replace.quickfix"),
                  "class X {" +
                  "  void m() {" +
                  "    Class<? extends String[]> aClass = String[].class;" +
                  "  }" +
                  "}");
  }

  public void testLocalClasses() {
    doTest("class X {" +
           "  void m() {" +
           "    class A {" +
           "      class B {" +
           "      }" +
           "    }" +
           "    Class<?> clazz = /*Instantiating object to get Class object*/new A()/*_*/.new B().getClass()/**/;" +
           "  }" +
           "}");
    checkQuickFix(InspectionGadgetsBundle.message("instantiating.object.to.get.class.object.replace.quickfix"),
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

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new InstantiatingObjectToGetClassObjectInspection();
  }
}