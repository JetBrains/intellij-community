package com.intellij.java.injection;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.CheckTagEmptyBodyInspection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static com.intellij.java.injection.InjectedLanguageFixtureKt.injectedLanguageFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
@ExtendWith(InjectedLanguageInvariantsExtension.class)
public class InjectedLanguageSuppressionTest {
  private static final Logger LOG = Logger.getInstance(InjectedLanguageSuppressionTest.class);
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = injectedLanguageFixture(
    projectFixture, () -> List.of(new CheckTagEmptyBodyInspection(), new SillyAssignmentInspection()),
    LightDaemonAnalyzerOptions.DEFAULT.withBasePath("/java/java-tests/testData/injection/suppressions/"));

  @Test
  public void testSuppressInjectionInHost() {
    doTest("Suppress for class");
  }

  @Test
  public void testSuppressInjectionInInjection() {
    doTest("Suppress for tag in injection");
  }

  @Test
  public void testSuppressInjectionInHostSameLanguage() {
    doTest("Suppress for field");
  }

  @Test
  public void testSuppressInjectionInInjectionSameLanguage() {
    doTest("Suppress for class in injection");
  }

  @Test
  public void testDoNotShowHostSuppressionsAvailableInInjectionsOnly() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureByFile("before" + daemon.getFixture().getTestName(false) + ".java");
    daemon.bringRealEditorBack();
    List<IntentionAction> actions = daemon.getAvailableActions();
    LOG.debug(StringUtil.join(actions, IntentionAction::getText, "\n"));
    assertNull(ContainerUtil.find(actions, action -> action.getText().equals("Suppress for statement with comment")));
  }

  public void doTest(final String actionName) {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    daemon.configureByFile("before" + myFixture.getTestName(false) + ".java");
    daemon.bringRealEditorBack();
    daemon.doAction(actionName);
    ReadAction.runBlocking(() -> PsiTestUtil.checkFileStructure(myFixture.getFile()));
    myFixture.checkResultByFile("after" + myFixture.getTestName(false) + ".java");
  }
}
