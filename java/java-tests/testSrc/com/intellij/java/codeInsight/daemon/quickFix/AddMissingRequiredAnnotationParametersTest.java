/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
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
