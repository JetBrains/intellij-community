// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class NavigateToAlreadyDeclaredVariableFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNavigateToVariableOfDifferentType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", "class A {{int i = 0; i++; long <caret>i = 0;}}");
    IntentionAction intention = myFixture.findSingleIntention(QuickFixBundle.message("navigate.variable.declaration.text", "i"));
    assertNotNull(intention);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(intention);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      assertEquals(14, myFixture.getCaretOffset());
    });
  }

  @Test
  public void testNavigateFromParameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", """
      class A {void f(String[] elements){
              String element = "hello";
      for (String el<caret>ement : elements){}}}""");
    IntentionAction intention = myFixture.findSingleIntention(QuickFixBundle.message("navigate.variable.declaration.text", "element"));
    assertNotNull(intention);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(intention);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      assertEquals(51, myFixture.getCaretOffset());
    });
  }
}
