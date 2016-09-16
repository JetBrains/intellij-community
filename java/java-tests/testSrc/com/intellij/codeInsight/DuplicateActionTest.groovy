/*
 * Copyright 2000-2016 JetBrains s.r.o.
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


import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NonNls

class DuplicateActionTest extends LightCodeInsightFixtureTestCase {
  void testOneLine() {
    doTest '''xxx<caret>
''', "txt", '''xxx
xxx<caret>
'''
  }

  void testEmpty() {
    doTest '<caret>', "txt", '<caret>'
  }

  private void doTest(String before, @NonNls String ext, String after) {
    myFixture.configureByText("a." + ext, before)
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE)
    myFixture.checkResult(after)
  }

  void testSelectName() {
    doTest '''
class C {
  void foo() {}<caret>
}
''', 'java', '''
class C {
  void foo() {}
  void <caret>foo() {}
}
'''
  }

  void "test preserve caret position when it's already inside element's name"() {
    doTest '''
class C {
  void fo<caret>o() {}
}
''', 'java', '''
class C {
  void foo() {}
  void fo<caret>o() {}
}
'''
  }

  void testXmlTag() {
    doTest '''
<root>
  <foo/><caret>
</root>
''', 'xml', '''
<root>
  <foo/>
  <foo/><caret>
</root>
'''
  }
}
