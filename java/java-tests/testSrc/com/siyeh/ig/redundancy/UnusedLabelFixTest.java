// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@TestApplication
public class UnusedLabelFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnusedLabelInspection()));

  @Test
  public void testRemoveLabel() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("unused.label.remove.quickfix"),
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

  @Test
  public void testDoNotFixUsedLabel() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unused.label.remove.quickfix"),
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
