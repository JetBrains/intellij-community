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
package com.intellij.java.codeInsight.template;

import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class TemplateBuilderTest extends LightCodeInsightFixtureTestCase {
  public void testRunInlineTemplate() {
    TemplateManagerImpl.setTemplateTesting(myFixture.getProject(), myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", "class A {\n" +
                                        "  public String tes<caret>t() {\n" +
                                        "  }\n" +
                                        "}");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> TemplateBuilderFactory
      .getInstance().createTemplateBuilder(myFixture.getElementAtCaret()).run(myFixture.getEditor(), true));
    myFixture.checkResult("class A {\n" +
                          "  <caret>public String test() {\n" +
                          "  }\n" +
                          "}");
  }
}
