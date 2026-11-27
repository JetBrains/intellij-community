// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.controlflow.UnnecessaryContinueInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@TestApplication
public class UnnecessaryContinueFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnnecessaryContinueInspection()));

  @Test
  public void testRemoveContinue() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "continue"),
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

  @Test
  public void testRemoveContinueIntoIf() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "continue"),
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

  @Test
  public void testDoNotFixFollowedContinue() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "continue"),
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
