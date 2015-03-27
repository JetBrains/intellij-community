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
package com.intellij.codeInsight
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.refactoring.typeMigration.intentions.ChangeClassParametersIntention
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * User: anna
 */
class ChangeClassParametersTest extends LightCodeInsightFixtureTestCase {
  public void "test nested type elements"() {
    def text = """\
     interface Fun<A, B> {}
     class Test {
       {
          new Fun<java.util.List<Int<caret>eger>, String> () {};
       }
     }
   """

    doTest(text)
  }

  private doTest(String text) {
    myFixture.configureByText("a.java", text)
    try {
      myFixture.doHighlighting()
      myFixture.launchAction(new ChangeClassParametersIntention())
    }
    finally {
      TemplateState state = TemplateManagerImpl.getTemplateState(editor)
      assertNull(state)
    }
  }
}