// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import static com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2.GTDUOutcome;
import static com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2.testGTDUOutcomeInNonBlockingReadAction;

public final class JavaRecordHeaderNavigationTest extends LightJavaCodeInsightFixtureTestCase {
  public void testRecordHeaderNavigation() {
    myFixture.configureByText("RecordTest.java", """
      public class RecordTest {
        record Rec<caret>(int x) {
        }
      
        void test(Rec rec) {
          Rec rec2 = new Rec(1);
        }
      }
      """);
    GTDUOutcome outcome = testGTDUOutcomeInNonBlockingReadAction(
      myFixture.getEditor(), myFixture.getFile(), myFixture.getCaretOffset());
    assertEquals(GTDUOutcome.SU, outcome);
  }
}
