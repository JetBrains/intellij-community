// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class VariableTypeTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/variableType";
  }

  public static class PreviewTest extends LightJavaCodeInsightFixtureTestCase {
    public void testSameFile() {
      myFixture.configureByText("Test.java", """
        class Test {
        void test() {
          int x = <caret>"xyz";
        }
        }""");
      IntentionAction action = myFixture.findSingleIntention("Change variable 'x' type to 'String'");
      assertNotNull(action);
      String text = IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor());
      assertEquals("""
                     class Test {
                     void test() {
                       String x = "xyz";
                     }
                     }""", text);
    }

    public void testAnotherFile() {
      myFixture.addClass("class Another {static int X = 123;}");
      myFixture.configureByText("Test.java", """
        class Test {
        void test() {
          Another.X = <caret>"xyz";
        }
        }""");
      IntentionAction action = myFixture.findSingleIntention("Change field 'X' type to 'String'");
      assertNotNull(action);
      String text = IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor());
      assertEquals("static String X = ...;", text);
    }

    public void testAnotherFileNoInitializer() {
      myFixture.addClass("class Another {static int X;}");
      myFixture.configureByText("Test.java", """
        class Test {
        void test() {
          Another.X = <caret>"xyz";
        }
        }""");
      IntentionAction action = myFixture.findSingleIntention("Change field 'X' type to 'String'");
      assertNotNull(action);
      String text = IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor());
      assertEquals("static String X;", text);
    }
  }
}
