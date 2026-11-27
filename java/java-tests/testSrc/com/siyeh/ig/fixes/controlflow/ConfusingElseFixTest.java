// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.controlflow.ConfusingElseInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@TestApplication
public class ConfusingElseFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ConfusingElseInspection()));

  @Test
  public void testRemoveElse() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix"),
                 """
                     public void printName(String name) {
                       if (name == null) {
                           throw new IllegalArgumentException();
                       } else/**/ {
                           System.out.println(name);
                       }
                   }
                   """,
                 """
                     public void printName(String name) {
                       if (name == null) {
                           throw new IllegalArgumentException();
                       }
                       System.out.println(name);
                   }
                   """
    );
  }

  @Test
  public void testReturnStatement() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix"),
                 """
                     public int printName(String name) {
                       if (name == null) {
                           return -1;
                       } else/**/ {
                           System.out.println(name);
                       }
                       return 0;
                     }
                   """,
                 """
                     public int printName(String name) {
                       if (name == null) {
                           return -1;
                       }
                       System.out.println(name);
                       return 0;
                     }
                   """
    );
  }

  @Test
  public void testBreakStatement() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix"),
                 """
                     public void printName(String[] texts) {
                       for (String text : texts) {
                         if ("illegal".equals(text)) {
                           break;
                         } else/**/ {
                           System.out.println(text);
                         }
                       }
                     }
                   """,
                 """
                     public void printName(String[] texts) {
                       for (String text : texts) {
                         if ("illegal".equals(text)) {
                           break;
                         }
                           System.out.println(text);
                       }
                     }
                   """
    );
  }

  @Test
  public void testContinueStatement() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix"),
                 """
                     public void printName(String[] texts) {
                       for (String text : texts) {
                         if ("illegal".equals(text)) {
                           continue;
                         } else/**/ {
                           System.out.println(text);
                         }
                       }
                     }
                   """,
                 """
                     public void printName(String[] texts) {
                       for (String text : texts) {
                         if ("illegal".equals(text)) {
                           continue;
                         }
                           System.out.println(text);
                       }
                     }
                   """
    );
  }

  @Test
  public void testDoNotFixElseWithoutJump() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("redundant.else.unwrap.quickfix"),
                               """
                                 class X {
                                   public void printName(String name) {
                                     if (name == null) {
                                         System.out.println("illegal");
                                     } else/**/ {
                                         System.out.println(name);
                                     }
                                   }
                                 }
                                 """);
  }
}
