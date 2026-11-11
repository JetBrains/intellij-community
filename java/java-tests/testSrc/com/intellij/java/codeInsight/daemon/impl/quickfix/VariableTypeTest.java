// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

public class VariableTypeTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/variableType";
  }

  @TestApplication
  public static class PreviewTest {
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
    private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

    @Test
    public void testSameFile() {
      LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
      myFixture.configureByText("Test.java", """
        class Test {
        void test() {
          int x = <caret>"xyz";
        }
        }""");
      IntentionAction action = myFixture.findSingleIntention("Change variable 'x' type to 'String'");
      Assertions.assertNotNull(action);
      String text = ReadAction.computeBlocking(() -> IntentionPreviewPopupUpdateProcessor.getPreviewText(myFixture.getProject(), action, myFixture.getFile(), myFixture.getEditor()));
      Assertions.assertEquals("""
                     class Test {
                     void test() {
                       String x = "xyz";
                     }
                     }""", text);
    }

    @Test
    public void testAnotherFile() {
      LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
      myFixture.addClass("class Another {static int X = 123;}");
      myFixture.configureByText("Test.java", """
        class Test {
        void test() {
          Another.X = <caret>"xyz";
        }
        }""");
      IntentionAction action = myFixture.findSingleIntention("Change field 'X' type to 'String'");
      Assertions.assertNotNull(action);
      String text = ReadAction.computeBlocking(() -> IntentionPreviewPopupUpdateProcessor.getPreviewText(myFixture.getProject(), action, myFixture.getFile(), myFixture.getEditor()));
      Assertions.assertEquals("static String X = ...;", text);
    }

    @Test
    public void testAnotherFileNoInitializer() {
      LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
      myFixture.addClass("class Another {static int X;}");
      myFixture.configureByText("Test.java", """
        class Test {
        void test() {
          Another.X = <caret>"xyz";
        }
        }""");
      IntentionAction action = myFixture.findSingleIntention("Change field 'X' type to 'String'");
      Assertions.assertNotNull(action);
      String text = ReadAction.computeBlocking(() -> IntentionPreviewPopupUpdateProcessor.getPreviewText(myFixture.getProject(), action, myFixture.getFile(), myFixture.getEditor()));
      Assertions.assertEquals("static String X;", text);
    }
  }
}
