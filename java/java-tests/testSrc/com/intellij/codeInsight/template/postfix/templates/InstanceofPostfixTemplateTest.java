/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import org.jetbrains.annotations.NotNull;

public class InstanceofPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testSingleExpression() {
    doTest();
  }

  public void testAlias() {
    doTest();
  }

  public void testPrimitive() {
    doTest();
  }

  public void testSingleExpressionTemplate() {
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());

    myFixture.configureByFile(getTestName(true) + ".java");
    myFixture.type('\t');

    TemplateState templateState = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
    assertNotNull(templateState);
    assertFalse(templateState.isFinished());

    myFixture.type("Integer");
    templateState.nextTab();
    assertTrue(templateState.isFinished());

    myFixture.checkResultByFile(getTestName(true) + "_after.java", true);
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "instanceof";
  }
}
