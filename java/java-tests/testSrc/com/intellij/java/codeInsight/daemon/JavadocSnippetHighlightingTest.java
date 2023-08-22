// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class JavadocSnippetHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new JavadocDeclarationInspection());
  }

  public void testSnippetExternalMismatch() {
    myFixture.addFileToProject("snippet-files/Test.java", """
      class Test {
          // @start region=reg
          // @replace region substring="one" replacement="two"
          void one() {}
          void anotherone() {} // @highlight substring="void"
          // @end
          // @end
      }
      """);
    myFixture.configureByText("Test.java", """
      /**
       * {<warning descr="External snippet differs from inline snippet">@<caret>snippet</warning> class="Test":
       * }
       */
      class X {}
      """);
    myFixture.checkHighlighting();
    IntentionAction deleteSnippetBody = myFixture.findSingleIntention("Remove snippet body");
    assertEquals("""
                   /**
                    * {@snippet class="Test"}
                    */
                   class X {}
                   """, myFixture.getIntentionPreviewText(deleteSnippetBody));
    IntentionAction action = myFixture.findSingleIntention("Synchronize inline snippet");
    myFixture.checkPreviewAndLaunchAction(action);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResult("""
                            /**
                             * {@snippet class="Test":
                             * class Test {
                             *
                             *
                             *     void two() {}
                             *     void anothertwo() {}
                             *
                             *
                             * }}
                             */
                            class X {}
                            """);
  }


}
