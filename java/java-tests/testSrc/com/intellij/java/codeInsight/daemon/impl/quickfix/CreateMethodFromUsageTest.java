// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

public class CreateMethodFromUsageTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (getTestName(false).endsWith("GenerateFinal.java")) {
      JavaCodeStyleSettings.getInstance(getProject()).GENERATE_FINAL_PARAMETERS = true;
    }
  }

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

  @TestApplication
  public static class PreviewTest {
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
    private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

    @Test
    public void testAnotherFile() {
      LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
      myFixture.addClass("class Another { }");
      myFixture.configureByText("Test.java", """
        class Test {
          void test() {
            Another.fo<caret>o();
          }
        }""");
      IntentionAction action = myFixture.findSingleIntention("Create method 'foo' in 'Another'");
      Assertions.assertNotNull(action);
      String text = ReadAction.computeBlocking(() -> IntentionPreviewPopupUpdateProcessor.getPreviewText(myFixture.getProject(), action, myFixture.getFile(), myFixture.getEditor()));
      Assertions.assertEquals("""
      class Another {
          public static void foo() {
             \s
          }
      }""", text);
    }
  }
}
