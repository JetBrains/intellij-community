// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.threading;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.threading.EmptySynchronizedStatementInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@TestApplication
public class EmptySynchronizedStatementFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new EmptySynchronizedStatementInspection()));

  @Test
  public void testRemoveSynchronizedStatement() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "synchronized"),
                 """
                     public void printName(String lock) {
                       synchronized/**/(lock) {}
                   }
                   """,
                 """
                     public void printName(String lock) {
                   }
                   """
    );
  }

  @Test
  public void testDoNotFixUsedSynchronizedStatement() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("smth.unnecessary.remove.quickfix", "synchronized"),
                               """
                                 class X {
                                   public void printName(String lock) {
                                     synchronized(lock) {
                                       System.out.println(lock);
                                     }
                                   }
                                 }
                                 """);
  }
}
