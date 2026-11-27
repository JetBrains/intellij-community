// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.controlflow.UnnecessaryBreakInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@SuppressWarnings("UnnecessaryBreak")
@TestApplication
public class UnnecessaryBreakFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnnecessaryBreakInspection()), null, null,
                        builder -> builder.setLanguageLevel(LanguageLevel.JDK_16));

  @Test
  public void testRemoveBreakOnCase() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "break"),
                 """
                     public void printName(String name) {
                       switch (name) {
                         case "A" -> {
                             System.out.println("A");
                             break/**/; // reports 'break' statement is unnecessary
                         }
                         default -> {
                             System.out.println("Default");
                             break; // reports 'break' statement is unnecessary
                         }
                       }
                     }
                   """,
                 """
                     public void printName(String name) {
                       switch (name) {
                         case "A" -> {
                             System.out.println("A");
                             // reports 'break' statement is unnecessary
                         }
                         default -> {
                             System.out.println("Default");
                             break; // reports 'break' statement is unnecessary
                         }
                       }
                     }
                   """
    );
  }

  @Test
  public void testRemoveBreakOnDefault() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "break"),
                 """
                     public void printName(String name) {
                       switch (name) {
                         case "A" -> {
                             System.out.println("A");
                             break; // reports 'break' statement is unnecessary
                         }
                         default -> {
                             System.out.println("Default");
                             break/**/; // reports 'break' statement is unnecessary
                         }
                       }
                     }
                   """,
                 """
                     public void printName(String name) {
                       switch (name) {
                         case "A" -> {
                             System.out.println("A");
                             break; // reports 'break' statement is unnecessary
                         }
                         default -> {
                             System.out.println("Default");
                             // reports 'break' statement is unnecessary
                         }
                       }
                     }
                   """
    );
  }

  @Test
  public void testDoNotFixClassicSwitch() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "break"),
                               """
                                 class X {
                                   public void printName(String name) {
                                     switch (name) {
                                       case "A" : {
                                           System.out.println("A");
                                           break/**/;
                                       }
                                       default : {
                                           System.out.println("Default");
                                           break;
                                       }
                                     }
                                   }
                                 }
                                 """);
  }

  @Test
  public void testDoNotFixLabelledBreak() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "break"),
                               """
                                 class X {
                                   public void printName(String[] names) {
                                     label: for (String name : names) {
                                       switch (name) {
                                         case "A" -> {
                                             System.out.println("A");
                                             break/**/ label;
                                         }
                                         default -> {
                                             System.out.println("Default");
                                             break label;
                                         }
                                       }
                                     }
                                   }
                                 }
                                 """);
  }
}
