/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.template

import com.intellij.codeInsight.template.impl.InvokeTemplateAction
import com.intellij.codeInsight.template.impl.SurroundWithTemplateHandler
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class SurroundWithTemplateTest extends LightCodeInsightFixtureTestCase {
  public void testSurroundWithTryWithResources() {
    myFixture.configureByText "C.java", """\
      import java.io.*;
      class C {
          void m() {
              new FileReader("/dev/null")<caret>
          }
      }""".stripIndent()

    invokeTemplate("TR")

    myFixture.checkResult """\
      import java.io.*;
      class C {
          void m() {
              try (FileReader fileReader = new FileReader("/dev/null")) {
                  <caret>
              }

          }
      }""".stripIndent()
  }

  private void invokeTemplate(String template) {
    DefaultActionGroup group = SurroundWithTemplateHandler.createActionGroup(project, editor, file)
    assertNotNull(group)
    InvokeTemplateAction action = group.childActionsOrStubs.find {
      it instanceof InvokeTemplateAction && (it as InvokeTemplateAction).template.key == template
    } as InvokeTemplateAction
    assertNotNull(action)
    action.perform()
  }
}
