// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;

/**
 * @author Dmitry Batkovich
 */
public class AddMissingRequiredAnnotationParametersTest extends LightQuickFixTestCase {

  private void doTest() {
    doSingleTest(getTestName(false) + ".java");
  }

  public void testFewParameters() {
    doTest();
  }

  public void testFewParameters2() {
    doTest();
  }

  public void testFewParameters3() {
    doTest();
  }

  public void testFewParametersWithoutOrder() {
    doTest();
  }

  public void testSingleParameter() {
    doTest();
  }

  public void testParameterWithDefaultValue() {
    doTest();
  }

  public void testValueTyping() {
    configureByFile(getBasePath() + "/beforeValueTyping.java");
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    doAction("Add missing annotation parameters - value3, value2, value1");
    final TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
    assertNotNull(state);
    type("\"value33\"");
    state.nextTab();
    type("\"value22\"");
    state.nextTab();
    type("\"value11\"");
    state.nextTab();
    assertTrue(state.isFinished());
    checkResultByFile(getBasePath() + "/afterValueTyping.java");
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/addMissingRequiredAnnotationParameters";
  }
}
