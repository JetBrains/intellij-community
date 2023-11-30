// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ClassWithOnlyPrivateConstructorsInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doTest("class /*Class 'X' with only 'private' constructors should be declared 'final'*/X/**/ {" +
           "  private X() {}" +
           "  private X(int i) {}" +
           "}");
  }

  public void testExtendingInnerClass() {
    doTest("""
             class X {
               private X() {}
               class Y {
                 class Z extends X{}
               }
             }""");
  }

  public void testNoConstructors() {
    doTest("class X {}");
  }

  public void testNoWarnOnFinalClass() {
    doTest("final class X {" +
           "  private X() {}" +
           "}");
  }

  public void testNoWarnOnAnonymInheritor() {
    doTest("class X {" +
           "  private X() {}" +
           "  static {new X() {};} " +
           "}");
  }

  public void testEnum() {
    //noinspection UnnecessaryEnumModifier
    doTest("""
             enum Currencies {
                 EURO, DOLLAR;
                 private Currencies() {
                 }
             }""");
  }

  public void testPublicConstructor() {
    doTest("class A {" +
           "  public A() {}" +
           "}");
  }

  public void testSubclassInSameFile() {
    doTest("class Test {" +
           "    private static class Inner {" +
           "        private Inner() {}" +
           "    }" +
           "    private static class InnerSub extends Inner {}" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ClassWithOnlyPrivateConstructorsInspection();
  }
}