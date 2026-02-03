// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.controlflow.UnnecessaryBreakInspection;

/**
 * @author Fabrice TIERCELIN
 */
@SuppressWarnings("UnnecessaryBreak")
public class UnnecessaryBreakFixTest extends IGQuickFixesTestCase {
  @Override
  protected void tuneFixture(final JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_16);
  }

  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryBreakInspection();
  }

  public void testRemoveBreakOnCase() {
    doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "break"),
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

  public void testRemoveBreakOnDefault() {
    doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "break"),
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

  public void testDoNotFixClassicSwitch() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "break"),
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

  public void testDoNotFixLabelledBreak() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "break"),
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
