// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class CreateInnerClassFromNewPreviewTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNoPreviewWhenTargetClassIsInAnotherFile() {
    myFixture.addClass("public class B { }");
    myFixture.configureByText("Test.java", """
      public class Test {
        void f() {
          new B.<caret>Builder();
        }
      }""");
    IntentionAction action = myFixture.findSingleIntention("Create inner class 'Builder'");
    assertNull(myFixture.getIntentionPreviewText(action));
  }

  public void testPreviewWhenTargetClassIsInTheSameFile() {
    myFixture.configureByText("Test.java", """
      public class Test {
        void f() {
          new B.<caret>Builder();
        }
      }
      class B { }""");
    IntentionAction action = myFixture.findSingleIntention("Create inner class 'Builder'");
    assertNotNull(myFixture.getIntentionPreviewText(action));
  }
}
