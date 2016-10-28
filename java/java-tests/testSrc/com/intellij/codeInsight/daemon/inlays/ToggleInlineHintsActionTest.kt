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
package com.intellij.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.isPossibleHintNearOffset
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat

class ToggleInlineHintsActionTest : LightCodeInsightFixtureTestCase() {

  private var before: Boolean = false

  override fun setUp() {
    super.setUp()
    before = EditorSettingsExternalizable.getInstance().isShowParameterNameHints
    EditorSettingsExternalizable.getInstance().isShowParameterNameHints = false
  }

  override fun tearDown() {
    EditorSettingsExternalizable.getInstance().isShowParameterNameHints = before
    super.tearDown()
  }

  fun `test is enabled near method with possible hints`() {
    myFixture.configureByText("A.java", """"
class Test {
  Test(int time) {}
  void initialize(int loadTime) {}
  static void s_test() {
    Test test = new T<caret>est(<caret>10);
    test.initial<caret>ize(100<caret>00);
  }
}
""")

    val file = myFixture.file
    val caretModel = myFixture.editor.caretModel

    assertThat(caretModel.caretCount).isEqualTo(4)

    editor.caretModel.allCarets.forEach {
      val hintCanBeAtCaret = isPossibleHintNearOffset(file, it.offset)
      assertThat(hintCanBeAtCaret).isTrue()
    }
  }
  
  fun `test is disabled in random places`() {
    myFixture.configureByText("A.java", """"
class Test {
  static void s_<caret>test() {
    int a <caret>= 2;
    Li<caret>st<String> list = null;
  }
}
""")

    val file = myFixture.file
    val caretModel = myFixture.editor.caretModel

    assertThat(caretModel.caretCount).isEqualTo(3)

    editor.caretModel.allCarets.forEach {
      val hintCanBeAtCaret = isPossibleHintNearOffset(file, it.offset)
      assertThat(hintCanBeAtCaret).isFalse()
    }
  }
  
}