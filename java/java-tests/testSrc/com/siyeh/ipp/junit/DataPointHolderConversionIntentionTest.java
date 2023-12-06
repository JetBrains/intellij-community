// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.junit;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.modcommand.*;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class DataPointHolderConversionIntentionTest extends LightQuickFixTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.STATIC_FIELD_NAME_PREFIX = "qwe";
    settings.STATIC_FIELD_NAME_SUFFIX = "asd";
  }

  private void doTest() {
    doSingleTest(getTestName(false) + ".java");
  }

  public void testField() {
    doTest();
  }

  public void testField2() {
    doTest();
  }

  public void testMethod() {
    doTest();
  }

  public void testMethod2() {
    doTest();
  }

  public void testMethod3() {
    doTest();
  }

  public void testMethod4() {
    doTest();
  }

  public void testMethod5() {
    doTest();
  }

  public void testNameTyping() {
    configureByFile(getBasePath() + "/beforeNameTyping.java");
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    IntentionAction intentionAction = findActionWithText("Replace by @DataPoint method");
    ModCommandAction mc = intentionAction.asModCommandAction();
    assertNotNull(mc);
    List<ModCommand> commands = mc.perform(ActionContext.from(getEditor(), getFile())).unpack();
    assertEquals(2, commands.size());
    assertInstanceOf(commands.get(0), ModUpdateFileText.class);
    String actualText = ((ModUpdateFileText)commands.get(0)).newText();
    assertEquals("""
                   // "Replace by @DataPoint method" "true"
                   class Foo {
                                      
                       @org.junit.experimental.theories.DataPoint
                       public static String bar() {
                           return null;
                       }
                                      
                   }""", actualText);
    assertInstanceOf(commands.get(1), ModStartRename.class);
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/java/java-tests/testData/ipp/com/siyeh/ipp/junit/";
  }

  @Override
  protected String getBasePath() {
    return "dataPointHolders";
  }
}
