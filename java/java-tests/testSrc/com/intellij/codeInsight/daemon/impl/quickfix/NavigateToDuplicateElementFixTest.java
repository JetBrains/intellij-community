// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class NavigateToDuplicateElementFixTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNavigateToSameLevelClass() {
    myFixture.configureByText("A.java", "class Foo {} class Foo<caret> {}");
    IntentionAction intention = myFixture.findSingleIntention("Navigate to duplicate class");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    assertEquals(6, myFixture.getCaretOffset());
  }

  public void testNavigateToSameLevelInterface() {
    myFixture.configureByText("A.java", "interface Foo {} interface Foo<caret> {}");
    IntentionAction intention = myFixture.findSingleIntention("Navigate to duplicate interface");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    assertEquals(10, myFixture.getCaretOffset());
  }

  public void testNavigateFromInterfaceToClass() {
    myFixture.configureByText("A.java", "class Foo { interface Foo<caret> {} }");
    IntentionAction intention = myFixture.findSingleIntention("Navigate to duplicate class");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    assertEquals(6, myFixture.getCaretOffset());
  }

  public void testNavigateFromClassToInterface() {
    myFixture.configureByText("A.java", "interface Foo {} class Foo<caret> {}");
    IntentionAction intention = myFixture.findSingleIntention("Navigate to duplicate interface");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    assertEquals(10, myFixture.getCaretOffset());
  }
}
