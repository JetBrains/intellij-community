// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
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

  public static class CustomTest extends LightJavaCodeInsightFixtureTestCase {
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
      assertEquals("static int test(int x, String y, StringBuilder z, Number a, ...)", text);
    }

    public void testStaleCrossFileReturnTypeCacheDoesNotCrashReturnFromVoidFix() {
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

      Document documentB = PsiDocumentManager.getInstance(getProject()).getDocument(classB.getContainingFile());
      assertNotNull(documentB);
      int offset = documentB.getText().indexOf("N[] n()");
      assertTrue("Expected 'N[] n()' in B.java", offset >= 0);
      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        documentB.replaceString(offset, offset + "N[]".length(), "void");
        String classString = "class N{}";
        int deleteClass = documentB.getText().indexOf(classString);
        documentB.deleteString(deleteClass, deleteClass + classString.length());
        PsiDocumentManager.getInstance(getProject()).commitDocument(documentB);
      });

      myFixture.doHighlighting();
    }
  }
}
