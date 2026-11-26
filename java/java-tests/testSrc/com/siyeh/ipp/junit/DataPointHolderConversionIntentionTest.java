// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.junit;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModStartRename;
import com.intellij.modcommand.ModUpdateFileText;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Dmitry Batkovich
 */
@TestApplication
public class DataPointHolderConversionIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(
    projectFixture, List::of,
    LightDaemonAnalyzerOptions.DEFAULT.withBasePath("/java/java-tests/testData/ipp/com/siyeh/ipp/junit/dataPointHolders"));

  @BeforeEach
  void setUp() {
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(daemonFixture.get().getFixture().getProject());
    settings.STATIC_FIELD_NAME_PREFIX = "qwe";
    settings.STATIC_FIELD_NAME_SUFFIX = "asd";
  }

  private void doTest() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.quickFixFixture().doTest(daemon.getFixture().getTestName(false) + ".java");
  }

  @Test
  public void testField() {
    doTest();
  }

  @Test
  public void testField2() {
    doTest();
  }

  @Test
  public void testMethod() {
    doTest();
  }

  @Test
  public void testMethod2() {
    doTest();
  }

  @Test
  public void testMethod3() {
    doTest();
  }

  @Test
  public void testMethod4() {
    doTest();
  }

  @Test
  public void testMethod5() {
    doTest();
  }

  @Test
  public void testNameTyping() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    daemon.configureByFile("/beforeNameTyping.java");
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    IntentionAction intentionAction = daemon.findActionWithText("Replace by @DataPoint method");
    ModCommandAction mc = intentionAction.asModCommandAction();
    assertNotNull(mc);
    List<ModCommand> commands =
      ReadAction.computeBlocking(() -> mc.perform(ActionContext.from(myFixture.getEditor(), myFixture.getFile())).unpack());
    assertEquals(2, commands.size());
    assertInstanceOf(ModUpdateFileText.class, commands.get(0));
    String actualText = ((ModUpdateFileText)commands.get(0)).newText();
    assertEquals("""
                   // "Replace by @DataPoint method" "true"
                   class Foo {
                                      
                       @org.junit.experimental.theories.DataPoint
                       public static String bar() {
                           return null;
                       }
                                      
                   }""", actualText);
    assertInstanceOf(ModStartRename.class, commands.get(1));
  }
}
