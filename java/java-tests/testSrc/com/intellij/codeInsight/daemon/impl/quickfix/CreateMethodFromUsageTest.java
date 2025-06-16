// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class CreateMethodFromUsageTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createMethodFromUsage";
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk21();
  }

  @Override
  protected ActionHint parseActionHintImpl(@NotNull PsiFile psiFile, @NotNull String contents) {
    return ActionHint.parse(psiFile, contents, false);
  }

  public static class PreviewTest extends LightJavaCodeInsightFixtureTestCase {
    public void testAnotherFile() {
      myFixture.addClass("class Another { }");
      myFixture.configureByText("Test.java", """
        class Test {
          void test() {
            Another.fo<caret>o();
          }
        }""");
      IntentionAction action = myFixture.findSingleIntention("Create method 'foo' in 'Another'");
      assertNotNull(action);
      String text = IntentionPreviewPopupUpdateProcessor.getPreviewText(getProject(), action, getFile(), getEditor());
      assertEquals("""
      class Another {
          public static void foo() {
             \s
          }
      }""", text);
    }
  }
}
