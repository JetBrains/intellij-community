// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.controlflow.UnnecessaryReturnInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@TestApplication
public class UnnecessaryReturnFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnnecessaryReturnInspection()));

  @Test
  public void testRemoveReturn() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "return"),
                 """
                     public void printName(String name) {
                       System.out.println(name);
                       return/**/;
                   }
                   """,
                 """
                     public void printName(String name) {
                       System.out.println(name);
                   }
                   """
    );
  }

  @Test
  public void testRemoveReturnIntoIf() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "return"),
                 """
                     public void printName(String name) {
                       if (!"foo".equals(name)) {
                         System.out.println(name);
                         return/**/;
                       }
                     }
                   """,
                 """
                     public void printName(String name) {
                       if (!"foo".equals(name)) {
                         System.out.println(name);
                       }
                     }
                   """
    );
  }

  @Test
  public void testRemoveReturnInConstructor() {
    ig.get().doTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "return"),

           """
             class X {
               public X(String name) {
                 if (!"foo".equals(name)) {
                   System.out.println(name);
                   return/**/;
                 }
               }
             }
             """,

           """
             class X {
               public X(String name) {
                 if (!"foo".equals(name)) {
                   System.out.println(name);
                 }
               }
             }
             """
    );
  }

  @Test
  public void testDoNotFixReturnWithValue() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "return"),
                               """
                                 class X {
                                   public int printName(String name) {
                                     System.out.println(name);
                                     return/**/ 0;
                                   }
                                 }
                                 """);
  }

  @Test
  public void testDoNotFixFollowedReturn() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "return"),
                               """
                                 class X {
                                   public void printName(String name) {
                                     if (!"foo".equals(name)) {
                                       System.out.println(name);
                                       return/**/;
                                     }
                                     System.out.println("Bad code");
                                   }
                                 }
                                 """);
  }
}
