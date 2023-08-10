// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.UnnecessaryLabelOnBreakStatementInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryLabelOnBreakStatementFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryLabelOnBreakStatementInspection();
  }

  public void testRemoveBreakLabel() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.label.remove.quickfix"),
                 """
                     public void printName(String name) {
                       label:
                       for (int i = 0; i < 10; i++) {
                         if (shouldBreak()) break label/**/;
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
                         if (shouldBreak()) break;
                         for (int j = 0; j < 10; j++) {
                           if (shouldBreak()) break label;
                           System.out.println("B");
                         }
                       }
                     }
                   """
    );
  }

  public void testRemoveBreakLabelOnWhile() {
    doMemberTest(InspectionGadgetsBundle.message("unnecessary.label.remove.quickfix"),
                 """
                     public void printName(int i) {
                       label:
                       while (i < 100) {
                         if (i == 50) break label/**/;
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
                         if (i == 50) break;
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

  public void testDoNotFixMeaningfulLabel() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.label.remove.quickfix"),
                               """
                                 class X {
                                   public void printName(String[] names) {
                                     label:
                                     for (int i = 0; i < 10; i++) {
                                       for (String name : names) {
                                         if ("A".equals(name)) break label/**/;
                                         System.out.println(i);
                                       }
                                     }
                                   }
                                 }
                                 """);
  }
}
