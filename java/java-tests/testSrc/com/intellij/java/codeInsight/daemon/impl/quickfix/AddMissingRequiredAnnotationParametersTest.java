// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Dmitry Batkovich
 */
@TestApplication
public class AddMissingRequiredAnnotationParametersTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(
    projectFixture, List::of,
    LightDaemonAnalyzerOptions.DEFAULT.withBasePath("/java/java-tests/testData/codeInsight/daemonCodeAnalyzer/quickFix/addMissingRequiredAnnotationParameters"));

  private void doTest() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.quickFixFixture().doTest(daemon.getFixture().getTestName(false) + ".java");
  }

  @Test
  public void testFewParameters() {
    doTest();
  }

  @Test
  public void testFewParameters2() {
    doTest();
  }

  @Test
  public void testFewParameters3() {
    doTest();
  }

  @Test
  public void testFewParametersWithoutOrder() {
    doTest();
  }

  @Test
  public void testSingleParameter() {
    doTest();
  }

  @Test
  public void testParameterWithDefaultValue() {
    doTest();
  }

  @Test
  public void testValueTyping() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    daemon.configureByFile("/beforeValueTyping.java");
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    daemon.doAction("Add missing annotation parameters - value3, value2, value1");
    final TemplateState state = EdtTestUtil.runInEdtAndGet(() -> TemplateManagerImpl.getTemplateState(myFixture.getEditor()));
    assertNotNull(state);
    daemon.type("\"value33\"");
    myFixture.runInCommand(() -> state.nextTab());
    daemon.type("\"value22\"");
    myFixture.runInCommand(() -> state.nextTab());
    daemon.type("\"value11\"");
    myFixture.runInCommand(() -> state.nextTab());
    assertTrue(state.isFinished());
    myFixture.checkResultByFile("afterValueTyping.java");
  }
}
