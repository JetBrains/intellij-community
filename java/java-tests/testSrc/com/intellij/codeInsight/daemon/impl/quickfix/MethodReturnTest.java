// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;


import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class MethodReturnTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/methodReturn";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_14;
  }

  public static class PreviewTest extends LightJavaCodeInsightFixtureTestCase {
    public void testAnotherFile() {
      myFixture.addClass("class Another {static void test(int x) {}}");
      myFixture.configureByText("Test.java", """
        class Test {
        void test() {
          int a = <caret>Another.test(123);
        }
        }""");
      IntentionAction action = myFixture.findSingleIntention("Make 'test()' return 'int'");
      assertNotNull(action);
      String text = IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor());
      assertEquals("static int test(int x)", text);
    }

    public void testAnotherFileManyParameters() {
      myFixture.addClass("class Another {static void test(int x, String y, StringBuilder z, Number a, Runnable r) {}}");
      myFixture.configureByText("Test.java", """
        class Test {
        void test() {
          int a = <caret>Another.test(123, null, null, null, null);
        }
        }""");
      IntentionAction action = myFixture.findSingleIntention("Make 'test()' return 'int'");
      assertNotNull(action);
      String text = IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor());
      assertEquals(text, "static int test(int x, String y, StringBuilder z, Number a, ...)");
    }
  }

}

