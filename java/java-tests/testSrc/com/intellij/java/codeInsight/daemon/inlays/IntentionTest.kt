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
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat

class BlackListMethodIntentionTest : LightCodeInsightFixtureTestCase() {

  private var isParamHintsEnabledBefore = false
  private val default = ParameterNameHintsSettings()
  
  override fun setUp() {
    super.setUp()

    val settings = EditorSettingsExternalizable.getInstance()
    isParamHintsEnabledBefore = settings.isShowParameterNameHints
    settings.isShowParameterNameHints = true
  }

  override fun tearDown() {
    EditorSettingsExternalizable.getInstance().isShowParameterNameHints = isParamHintsEnabledBefore
    ParameterNameHintsSettings.Companion.getInstance().loadState(default.state)

    super.tearDown()
  }
  
  fun `test add to blacklist by alt enter`() {
    myFixture.configureByText("a.java", """
class Test {
  void test() {
    check(<caret>100);
  }
  void check(int isShow) {}
}
""")
    assertHintExistsAndDisappearsAfterIntention()
  }
  
  fun `test add elements which has inlays`() {
    myFixture.configureByText("a.java", """
class ParamHintsTest {

    public static void main(String[] args) {
        Mvl(
                <caret>Math.abs(1) * 100, 32, 32
        );
    }

    public static double Mvl(double first, double x, double c) {
        return Double.NaN;
    }
}
""")
    assertHintExistsAndDisappearsAfterIntention()
  }
  
  private fun assertHintExistsAndDisappearsAfterIntention() {
    myFixture.doHighlighting()

    val caretOffset = editor.caretModel.offset
    val before = editor.inlayModel.getInlineElementsInRange(caretOffset, caretOffset)
    assertThat(before).isNotEmpty

    val intention = myFixture.getAvailableIntention("Do not show hints for current method")
    myFixture.launchAction(intention!!)
    myFixture.doHighlighting()

    val after = editor.inlayModel.getInlineElementsInRange(caretOffset, caretOffset)
    assertThat(after).hasSize(1)

    val text = ParameterHintsPresentationManager.getInstance().getHintText(after[0])
    assertThat(text).isNull()
  }
  
}