// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixOptions;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class CreateMethodFromUsageTest {
  private static final String TEST_DATA_PATH =
    JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/createMethodFromUsage";
  private static final TestFixture<LightJavaProject> quickFixProjectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.HIGHEST, IdeaTestUtil::getMockJdk21));
  private final TestFixture<LightQuickFixFixture> quickFixFixture =
    lightQuickFixFixture(quickFixProjectFixture, TEST_DATA_PATH, List::of, LightQuickFixOptions.DEFAULT.withPrefixActionHintMatch());

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    if (fileName.endsWith("GenerateFinal.java")) {
      JavaCodeStyleSettings.getInstance(quickFixFixture.get().getFixture().getProject()).GENERATE_FINAL_PARAMETERS = true;
    }
    quickFixFixture.get().doTest(fileName);
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
