/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.daemon.quickFix;


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
    public void testSameFile() {
      myFixture.configureByText("Test.java", "class Test {\n" +
                                             "void test() {\n" +
                                             "  int x = <caret>anotherMethod();\n" +
                                             "}\n" +
                                             "void anotherMethod() {}\n" +
                                             "}");
      IntentionAction action = myFixture.findSingleIntention("Make 'anotherMethod' return 'int'");
      assertNotNull(action);
      String text = IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor());
      assertEquals(text, "class Test {\n" +
                         "void test() {\n" +
                         "  int x = anotherMethod();\n" +
                         "}\n" +
                         "int anotherMethod() {}\n" +
                         "}");
    }

    public void testAnotherFile() {
      myFixture.addClass("class Another {static void test(int x) {}}");
      myFixture.configureByText("Test.java", "class Test {\n" +
                                             "void test() {\n" +
                                             "  int a = <caret>Another.test(123);\n" +
                                             "}\n" +
                                             "}");
      IntentionAction action = myFixture.findSingleIntention("Make 'test' return 'int'");
      assertNotNull(action);
      String text = IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor());
      assertEquals("static int test(int x)", text);
    }

    public void testAnotherFileManyParameters() {
      myFixture.addClass("class Another {static void test(int x, String y, StringBuilder z, Number a, Runnable r) {}}");
      myFixture.configureByText("Test.java", "class Test {\n" +
                                             "void test() {\n" +
                                             "  int a = <caret>Another.test(123, null, null, null, null);\n" +
                                             "}\n" +
                                             "}");
      IntentionAction action = myFixture.findSingleIntention("Make 'test' return 'int'");
      assertNotNull(action);
      String text = IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor());
      assertEquals(text, "static int test(int x, String y, StringBuilder z, Number a, ...)");
    }
  }

}

