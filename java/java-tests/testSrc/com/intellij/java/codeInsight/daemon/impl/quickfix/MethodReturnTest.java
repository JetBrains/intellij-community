// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

public class MethodReturnTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/methodReturn";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_14;
  }

  @TestApplication
  public static class CustomTest {
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
    private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

    @Test
    public void testAnotherFile() {
      LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
      myFixture.addClass("class Another {static void test(int x) {}}");
      myFixture.configureByText("Test.java", """
        class Test {
        void test() {
          int a = <caret>Another.test(123);
        }
        }""");
      IntentionAction action = myFixture.findSingleIntention("Make 'test()' return 'int'");
      Assertions.assertNotNull(action);
      String text = ReadAction.computeBlocking(() -> IntentionPreviewPopupUpdateProcessor.getPreviewText(myFixture.getProject(), action, myFixture.getFile(), myFixture.getEditor()));
      Assertions.assertEquals("static int test(int x)", text);
    }

    @Test
    public void testAnotherFileManyParameters() {
      LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
      myFixture.addClass("class Another {static void test(int x, String y, StringBuilder z, Number a, Runnable r) {}}");
      myFixture.configureByText("Test.java", """
        class Test {
        void test() {
          int a = <caret>Another.test(123, null, null, null, null);
        }
        }""");
      IntentionAction action = myFixture.findSingleIntention("Make 'test()' return 'int'");
      Assertions.assertNotNull(action);
      String text = ReadAction.computeBlocking(() -> IntentionPreviewPopupUpdateProcessor.getPreviewText(myFixture.getProject(), action, myFixture.getFile(), myFixture.getEditor()));
      Assertions.assertEquals("static int test(int x, String y, StringBuilder z, Number a, ...)", text);
    }

    @Test
    public void testStaleCrossFileReturnTypeCacheDoesNotCrashReturnFromVoidFix() {
      LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
      PsiClass classB = myFixture.addClass( "public class B { N[] n() { return null; } class N{}");
      myFixture.configureByText("A.java", """
        class A {
          void m() {
            if(1==1){
              return null;
            }
            <caret>return new B().n();
          }
        }""");

      myFixture.doHighlighting();

      Document documentB = PsiDocumentManager.getInstance(myFixture.getProject()).getDocument(classB.getContainingFile());
      Assertions.assertNotNull(documentB);
      int offset = documentB.getText().indexOf("N[] n()");
      Assertions.assertTrue(offset >= 0, "Expected 'N[] n()' in B.java");
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        documentB.replaceString(offset, offset + "N[]".length(), "void");
        String classString = "class N{}";
        int deleteClass = documentB.getText().indexOf(classString);
        documentB.deleteString(deleteClass, deleteClass + classString.length());
        PsiDocumentManager.getInstance(myFixture.getProject()).commitDocument(documentB);
      });

      myFixture.doHighlighting();
    }
  }
}
