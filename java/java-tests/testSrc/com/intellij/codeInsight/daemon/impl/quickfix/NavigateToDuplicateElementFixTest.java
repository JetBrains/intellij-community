// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class NavigateToDuplicateElementFixTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNavigateToSameLevelClass() {
    myFixture.configureByText("A.java", "class Foo {} class Foo<caret> {}");
    doTest("Navigate to duplicate class", 6);
  }

  public void testNavigateToSameLevelInterface() {
    myFixture.configureByText("A.java", "interface Foo {} interface Foo<caret> {}");
    doTest("Navigate to duplicate interface", 10);
  }

  public void testNavigateFromInterfaceToClass() {
    myFixture.configureByText("A.java", "class Foo { interface Foo<caret> {} }");
    doTest("Navigate to duplicate class", 6);
  }

  public void testNavigateFromClassToInterface() {
    myFixture.configureByText("A.java", "interface Foo {} class Foo<caret> {}");
    doTest("Navigate to duplicate interface", 10);
  }

  private void doTest(String intentionName, int expectedOffset) {
    IntentionAction intention = myFixture.findSingleIntention(intentionName);
    assertNotNull(intention);
    myFixture.launchAction(intention);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    assertEquals(expectedOffset, myFixture.getCaretOffset());
  }
}
