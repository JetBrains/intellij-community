// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class InstanceofIncompatibleInterfaceInspectionTest extends LightJavaInspectionTestCase {

  public void testHashMap() {
    doTest("""
             import java.util.HashMap;
             import java.util.List;
             class X {
               void m() {
                 if(new HashMap() instanceof /*'instanceof' with incompatible interface 'List'*/List/**/);
               }
             }""");
  }

  public void testClasses() {
    doTest("""
             class Foo { }
             interface Bar { }
             final class Main213 {
             
                 static void x(Foo f, Bar b) {
                     if (f instanceof /*'instanceof' with incompatible interface 'Bar'*/Bar/**/) {
                         System.out.println("fail");
                     }
                     if (b instanceof /*'instanceof' with incompatible class 'Foo'*/Foo/**/) {
                         System.out.println("fail");
                     }
                 }
             }""");
  }

  public void testNoWarningOnUncompilableCode() {
    doTest("""
             final class Foo { }
             interface Bar { }
             final class Main213 {
             
                 static void x(Foo f, Bar b) {
                     if (/*!Inconvertible types; cannot cast 'Foo' to 'Bar'*/f instanceof Bar/*!*/) {
                         System.out.println("fail");
                     }
                     if (/*!Inconvertible types; cannot cast 'Bar' to 'Foo'*/b instanceof Foo/*!*/) {
                         System.out.println("fail");
                     }
                 }
             }""");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new InstanceofIncompatibleInterfaceInspection();
  }
}
