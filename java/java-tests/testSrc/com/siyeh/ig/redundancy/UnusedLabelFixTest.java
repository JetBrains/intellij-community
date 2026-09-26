// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;

/**
 * @author Fabrice TIERCELIN
 */
public class UnusedLabelFixTest extends IGQuickFixesTestCase {
  @Override
  protected BaseInspection getInspection() {
    return new UnusedLabelInspection();
  }

  public void testRemoveLabel() {
    doMemberTest(InspectionGadgetsBundle.message("unused.label.remove.quickfix"),
                 """
                     public void myMethod(int count) {
                       label/**/: for (int i = 0; i < count; i++) {
                         if (i == 3) {
                           break;
                         }
                       }
                     }
                   """,

                 """
                     public void myMethod(int count) {
                       for (int i = 0; i < count; i++) {
                           if (i == 3) {
                               break;
                           }
                       }
                     }
                   """
    );
  }

  public void testDoNotFixUsedLabel() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unused.label.remove.quickfix"),
                               """
                                 class Example {
                                   public void myMethod(int count) {
                                     label/**/: for (int i = 0; i < count; i++) {
                                       for (int j = 0; j < count; j++) {
                                         if (i == 3) {
                                           break label;
                                         }
                                       }
                                     }
                                   }
                                 }
                                 """
    );
  }
}
