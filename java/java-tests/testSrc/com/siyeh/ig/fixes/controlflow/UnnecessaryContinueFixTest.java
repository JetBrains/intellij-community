// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.UnnecessaryContinueInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryContinueFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryContinueInspection();
  }

  public void testRemoveContinue() {
    doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "continue"),
                 """
                     public void printName(String[] names) {
                       for (String name : names) {
                         System.out.println(name);
                         continue/**/;
                       }
                     }
                   """,
                 """
                     public void printName(String[] names) {
                       for (String name : names) {
                         System.out.println(name);
                       }
                     }
                   """
    );
  }

  public void testRemoveContinueIntoIf() {
    doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "continue"),
                 """
                     public void printName(String[] names) {
                       for (String name : names) {
                         if (!"foo".equals(name)) {
                           System.out.println(name);
                           continue/**/;
                         }
                       }
                     }
                   """,
                 """
                     public void printName(String[] names) {
                       for (String name : names) {
                         if (!"foo".equals(name)) {
                           System.out.println(name);
                         }
                       }
                     }
                   """
    );
  }

  public void testDoNotFixFollowedContinue() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "continue"),
                               """
                                 class X {
                                   public void printName(String[] names) {
                                     for (String name : names) {
                                       if (!"foo".equals(name)) {
                                         System.out.println(name);
                                         continue/**/;
                                       }
                                       System.out.println("Ready for a new iteration");
                                     }
                                   }
                                 }
                                 """);
  }
}
