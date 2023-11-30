// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.UnnecessaryDefaultInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryDefaultFixTest extends IGQuickFixesTestCase {
  @Override
  protected void tuneFixture(final JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_16);
  }

  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryDefaultInspection();
  }

  public void testRemoveReturn() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.default.quickfix"),
                 """
                     enum E { A, B }
                     int foo(E e) {
                       return switch (e) {
                         case A -> 1;
                         case B -> 2;
                         default/**/ -> 3;
                       };
                     }
                   """,
                 """
                     enum E { A, B }
                     int foo(E e) {
                       return switch (e) {
                         case A -> 1;
                         case B -> 2;
                       };
                     }
                   """
    );
  }

  public void testDoNotFixWithMissingValues() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.default.quickfix"),
                               """
                                 class X {
                                   enum E { A, B, C }
                                   int foo(E e) {
                                     return switch (e) {
                                       case A -> 1;
                                       case B -> 2;
                                       default/**/ -> 3;
                                     };
                                   }
                                 }
                                 """);
  }

  public void testDoNotFixOnObject() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.default.quickfix"),
                               """
                                 class X {
                                   int foo(Character e) {
                                     return switch (e) {
                                       case 'A' -> 1;
                                       case 'B' -> 2;
                                       default/**/ -> 3;
                                     };
                                   }
                                 }
                                 """);
  }

  public void testDoNotFixOnPrimitive() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.default.quickfix"),
                               """
                                 class X {
                                   int foo(char e) {
                                     return switch (e) {
                                       case 'A' -> 1;
                                       case 'B' -> 2;
                                       default/**/ -> 3;
                                     };
                                   }
                                 }
                                 """);
  }
}
