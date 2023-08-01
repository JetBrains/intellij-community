// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("CastToIncompatibleInterface")
public class CastToIncompatibleInterfaceInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("class X { " +
           "  I list = (/*Cast to incompatible interface 'I'*/I/**/) new C(); " +
           "}" +
           "interface I {}" +
           "class C {}");
  }

  public void testHashMap() {
    doTest("import java.util.HashMap;" +
           "import java.util.List;" +
           "class X {" +
           "  List l = (/*Cast to incompatible interface 'List'*/List/**/) new HashMap();" +
           "}");
  }

  /** @noinspection InstanceofIncompatibleInterface*/
  public void testNullOrInstance() {
    doTest("class X { static I foo(X x) {if (x == null || x instanceof I) return (I)x;return null;}} interface I {}");
  }

  /** @noinspection InstanceofIncompatibleInterface*/
  public void testOrNot() {
    doTest("""
             class A {
               public boolean check(final boolean flag) {
                 return flag || !(getDelegate() instanceof Bar) ||
                        Boolean.TRUE.equals(((Bar)getDelegate()).getValue());
               }
               native Foo getDelegate();
               static class Foo {}
               interface Bar { Boolean getValue();}
             }""");
  }

  public void testCastToIncompatibleInterface() {
    doTest();
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new CastToIncompatibleInterfaceInspection();
  }
}
