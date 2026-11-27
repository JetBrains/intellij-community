// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.controlflow.UnnecessaryDefaultInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@TestApplication
public class UnnecessaryDefaultFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnnecessaryDefaultInspection()), null, null,
                        builder -> builder.setLanguageLevel(LanguageLevel.JDK_16));

  @Test
  public void testRemoveReturn() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("unnecessary.default.quickfix"),
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

  @Test
  public void testDoNotFixWithMissingValues() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.default.quickfix"),
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

  @Test
  public void testDoNotFixOnObject() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.default.quickfix"),
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

  @Test
  public void testDoNotFixOnPrimitive() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.default.quickfix"),
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
