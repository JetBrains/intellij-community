// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class BooleanParameterInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("class X {" +
           "  public void /*'public' method 'm()' with 'boolean' parameter*/m/**/(boolean b) {}" +
           "  public void n() {}" +
           "  public void o(int i) {}" +
           "  void p(boolean b) {}" +
           "}");
  }

  public void testConstructor() {
    doTest("class X {" +
           "  public /*'public' constructor 'X()' with 'boolean' parameter*/X/**/(boolean x) {}" +
           "}");
  }

  public void testSetter() {
    doTest("class X {" +
           "  public void setMyProperty(final boolean myProperty) {}" +
           "}");
  }

  public void testVarargs() {
    doTest("class Y {" +
           "    public Y(boolean... b) {}" +
           "}" +
           "class X extends Y {" +
           "    public /*'public' constructor 'X()' with 'boolean' parameter*/X/**/(boolean b) {" +
           "        super(true, b);" +
           "    }" +
           "}");
  }

  public void testInterfaceMethods() {
    doTest(
      """
          interface I {
            private boolean check(boolean b) { return !b; }
            private static boolean check2(boolean b) { return !b; }
            boolean /*'public' method 'check3()' with 'boolean' parameter*/check3/**/(boolean b);
          }
          """
    );
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new BooleanParameterInspection();
  }
}
