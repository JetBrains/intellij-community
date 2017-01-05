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

import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.openapi.editor.Inlay
import org.assertj.core.api.Assertions.assertThat

class BlackListMethodIntentionTest: InlayParameterHintsTest() {
  
  fun `test add to blacklist by alt enter`() {
    configureFile("a.java", """
class Test {
  void test() {
    check(<caret>100);
  }
  void check(int isShow) {}
}
""")

    val before = onLineStartingWith("check").inlays[0].getHintText()
    assertThat(before).isNotEmpty()
    
    val intention = myFixture.getAvailableIntention("Do not show hints for current method")
    myFixture.launchAction(intention!!)
    myFixture.doHighlighting()
    
    val after = onLineStartingWith("check").inlays[0].getHintText()
    assertThat(after).isNull()
  }

  private fun Inlay.getHintText() = ParameterHintsPresentationManager.getInstance().getHintText(this)

}