// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

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
      myFixture.configureByText("Test.java", "class Test {\n" +
                                             "void test() {\n" +
                                             "  int x = <caret>\"xyz\";\n" +
                                             "}\n" +
                                             "}");
      IntentionAction action = myFixture.findSingleIntention("Change variable 'x' type to 'String'");
      assertNotNull(action);
      String text = IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor());
      assertEquals("class Test {\n" +
                   "void test() {\n" +
                   "  String x = \"xyz\";\n" +
                   "}\n" +
                   "}", text);
    }

    public void testAnotherFile() {
      myFixture.addClass("class Another {static int X = 123;}");
      myFixture.configureByText("Test.java", "class Test {\n" +
                                             "void test() {\n" +
                                             "  Another.X = <caret>\"xyz\";\n" +
                                             "}\n" +
                                             "}");
      IntentionAction action = myFixture.findSingleIntention("Change field 'X' type to 'String'");
      assertNotNull(action);
      String text = IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor());
      assertEquals("static String X = ...;", text);
    }

    public void testAnotherFileNoInitializer() {
      myFixture.addClass("class Another {static int X;}");
      myFixture.configureByText("Test.java", "class Test {\n" +
                                             "void test() {\n" +
                                             "  Another.X = <caret>\"xyz\";\n" +
                                             "}\n" +
                                             "}");
      IntentionAction action = myFixture.findSingleIntention("Change field 'X' type to 'String'");
      assertNotNull(action);
      String text = IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor());
      assertEquals("static String X;", text);
    }
  }
}
