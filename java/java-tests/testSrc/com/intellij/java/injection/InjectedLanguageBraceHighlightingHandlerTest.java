package com.intellij.java.injection;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.highlighting.BraceHighlightingHandlerTest;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.intellij.java.injection.InjectedLanguageFixtureKt.injectedLanguageFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
@ExtendWith(InjectedLanguageInvariantsExtension.class)
public class InjectedLanguageBraceHighlightingHandlerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = injectedLanguageFixture(projectFixture);

  private static final String BASE_PATH = "/injection/braceHighlighter/";

  @Test
  public void testEmptyShred() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    daemon.configureByFile(BASE_PATH + myFixture.getTestName(true) + ".java");
    String result = EdtTestUtil.runInEdtAndGet(
      () -> BraceHighlightingHandlerTest.getEditorTextWithHighlightedBraces(myFixture.getEditor(), myFixture.getFile()));
    PlatformTestUtil.assertSameLinesWithFile(myFixture.getTestDataPath() + BASE_PATH + myFixture.getTestName(true) + ".java.txt", result);
  }
}
