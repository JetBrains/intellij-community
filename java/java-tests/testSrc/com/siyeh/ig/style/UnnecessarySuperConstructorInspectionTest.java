// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessarySuperConstructorInspectionTest extends LightJavaInspectionTestCase {

  public void testQualifiedSuper() {
    doTest("""
             class Outer {
               class Super {}
               class Inner extends Super {
                 Inner(Outer outer) {
                   outer.super();
                 }
               }
             }""");
  }

  public void testSimple() {
    doTest("""
             class Simple {
               Simple() {
                 /*'super()' is unnecessary*/super()/**/;
               }
             }""");
  }
  
  public void testFlexibleConstructorBody() {
    doTest("""
             class Friend {
               Friend() {
                 System.out.println(0);
               }
             }
             class Flexible extends Friend {
             
               Flexible() {
                 System.out.println("before");
                 super();
                 System.out.println("after");
               }
             }
             """);
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessarySuperConstructorInspection();
  }
}
