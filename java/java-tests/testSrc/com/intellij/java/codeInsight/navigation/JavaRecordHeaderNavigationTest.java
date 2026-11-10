// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2.GTDUOutcome;
import static com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2.testGTDUOutcomeInNonBlockingReadAction;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public final class JavaRecordHeaderNavigationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testRecordHeaderNavigation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("RecordTest.java", """
      public class RecordTest {
        record Rec<caret>(int x) {
        }
      
        void test(Rec rec) {
          Rec rec2 = new Rec(1);
        }
      }
      """);
    int offset = ReadAction.computeBlocking(() -> myFixture.getCaretOffset());
    GTDUOutcome outcome = testGTDUOutcomeInNonBlockingReadAction(myFixture.getEditor(), myFixture.getFile(), offset);
    assertEquals(GTDUOutcome.SU, outcome);
  }
}
