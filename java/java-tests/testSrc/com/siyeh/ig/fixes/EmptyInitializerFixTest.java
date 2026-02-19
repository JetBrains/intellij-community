// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.bugs.EmptyInitializerInspection;

public class EmptyInitializerFixTest extends LightJavaCodeInsightFixtureTestCase {
  public void testEmptyInitializer() {
    myFixture.enableInspections(new EmptyInitializerInspection());
    myFixture.configureByText("Test.java", """
      class X {
        <caret>{}
      }
      """);
    IntentionAction intention = myFixture.findSingleIntention("Delete empty class initializer");
    myFixture.checkPreviewAndLaunchAction(intention);
    myFixture.checkResult("""
                            class X {
                            }
                            """);
  }
}
