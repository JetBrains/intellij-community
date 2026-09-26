// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class UnnecessarilyQualifiedInnerClassAccessInspectionTest extends LightJavaInspectionTestCase {

  public void testRemoveQualifier() {
    doTest("""
             class X {
               /*'Y' is unnecessarily qualified with 'X'*//*_*/X/**//* 1*/./* 2*/Y foo;
              \s
               class Y{}
             }""");
    checkQuickFix("Remove qualifier", """
      class X {
        /* 2*//* 1*/ Y foo;
       \s
        class Y{}
      }""");
  }

  public void testRemoveQualifierWithImport() {
    doTest("""
             package p;
             import java.util.List;
             abstract class X implements List</*'Y' is unnecessarily qualified with 'X'*//*_*/X/**/.Y> {
               class Y{}
             }""");
    checkQuickFix("Remove qualifier", """
      package p;
      import p.X.Y;

      import java.util.List;
      abstract class X implements List<Y> {
        class Y{}
      }"""
    );
  }

  public void testUnnecessarilyQualifiedInnerClassAccess() {
    doTest();
    checkQuickFixAll();
  }

  public void testNoImports() {
    final UnnecessarilyQualifiedInnerClassAccessInspection inspection = new UnnecessarilyQualifiedInnerClassAccessInspection();
    inspection.ignoreReferencesNeedingImport = true;
    myFixture.enableInspections(inspection);
    doTest();
    checkQuickFixAll();
  }

  @Override
  protected BaseInspection getInspection() {
    return new UnnecessarilyQualifiedInnerClassAccessInspection();
  }
}
