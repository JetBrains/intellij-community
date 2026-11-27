// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.controlflow.UnnecessaryLabelOnContinueStatementInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@TestApplication
public class UnnecessaryLabelOnContinueStatementFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnnecessaryLabelOnContinueStatementInspection()));

  @Test
  public void testRemoveContinueLabel() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("unnecessary.label.remove.quickfix"),
                 """
                     public void printName(String name) {
                       label:
                       for (int i = 0; i < 10; i++) {
                         if (shouldBreak()) continue label/**/;
                         for (int j = 0; j < 10; j++) {
                           if (shouldBreak()) break label;
                           System.out.println("B");
                         }
                       }
                     }
                   """,
                 """
                     public void printName(String name) {
                       label:
                       for (int i = 0; i < 10; i++) {
                         if (shouldBreak()) continue;
                         for (int j = 0; j < 10; j++) {
                           if (shouldBreak()) break label;
                           System.out.println("B");
                         }
                       }
                     }
                   """
    );
  }

  @Test
  public void testRemoveContinueLabelOnWhile() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("unnecessary.label.remove.quickfix"),
                 """
                     public void printName(int i) {
                       label:
                       while (i < 100) {
                         if (i == 50) continue label/**/;
                         for (int j = 0; j < 10; j++) {
                           if (shouldBreak()) break label;
                           System.out.println("B");
                         }
                         i *= 2;
                       }
                     }
                   """,
                 """
                     public void printName(int i) {
                       label:
                       while (i < 100) {
                         if (i == 50) continue;
                         for (int j = 0; j < 10; j++) {
                           if (shouldBreak()) break label;
                           System.out.println("B");
                         }
                         i *= 2;
                       }
                     }
                   """
    );
  }

  @Test
  public void testDoNotFixMeaningfulLabel() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.label.remove.quickfix"),
                               """
                                 class X {
                                   public void printName(String[] names) {
                                     label:
                                     for (int i = 0; i < 10; i++) {
                                       for (String name : names) {
                                         if ("A".equals(name)) continue label/**/;
                                         System.out.println(i);
                                       }
                                     }
                                   }
                                 }
                                 """);
  }
}
