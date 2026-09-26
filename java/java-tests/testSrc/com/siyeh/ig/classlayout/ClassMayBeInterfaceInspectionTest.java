// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ClassMayBeInterfaceInspectionTest extends LightJavaInspectionTestCase {

  public void testOne() {
    doTest("""
             abstract class /*Abstract class 'ConvertMe' may be interface*/ConvertMe/**/ {
                 public static final String S = "";
                 public void m() {}
                 public static void n() {
                     new ConvertMe() {};
                     class X extends ConvertMe {}
                 }
                 public class A {}
             }""");
  }

  public void testOnTwo() {
    doTest("""
             class ConvertMe {
                 public static final String S = "";
                 public void m() {}
                 public static void n() {
                     new ConvertMe() {};
                     class X extends ConvertMe {}
                 }
                 public class A {}
             }""");
  }

  public void testMethodCantBeDefault() {
    doTest("""
             class Issue {
                 public abstract class Inner {
                     public Issue getParent() {
                         return Issue.this;
                     }
                 }
             }""");
  }

  public void testObjectMethods() {
    doTest("""
             abstract class X {
               public boolean equals(Object o) { return false; }
               public int hashCode() { return 1; }
               public String toString() { return null; }
             }""");
  }

  public void testLocalClassAccessingParameter() {
    doTest("""
             class Outer {
               void x(int parameter) {
                 abstract class Example {
                   public static final int MY_CONST = 42;
                   public abstract void foo();
             
                   public void g() {
                     System.out.println(parameter);
                   }
                 }
             
                 class Inheritor extends Example {
                   @Override
                   public void foo() {
                     System.out.println(MY_CONST);
                   }
                 }
               }
             }
             """);
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    final ClassMayBeInterfaceInspection inspection = new ClassMayBeInterfaceInspection();
    inspection.reportClassesWithNonAbstractMethods = true;
    return inspection;
  }
}
