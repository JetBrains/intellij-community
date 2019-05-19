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
package com.intellij.ide.projectWizard

import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.ComparisonUtil
import com.intellij.openapi.ui.MultiLineLabelUI
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import org.junit.Assert.assertTrue
import org.junit.Test

@RunWithIde(CommunityIde::class)
class CommandLineProjectGuiTest: GuiTestCase() {

  private val codeText: String = """package com.company;

public class Main {

  public static void main(String[] args) {
    // write your code here
  }
}
"""

  @Test
  fun testProjectCreate() {

    CommunityProjectCreator.createCommandLineProject()
    ideFrame {
      editor {
        //wait until current file has appeared in current editor and set focus to editor
        moveTo(1)
      }
      val editorCode = editor.getCurrentFileContents(false)
      assertTrue(ComparisonUtil.isEquals(codeText.unifyCode(),
                                         editorCode!!.unifyCode(),
                                         ComparisonPolicy.TRIM_WHITESPACES))
      closeProjectAndWaitWelcomeFrame()
    }
  }

  fun String.unifyCode(): String =
    MultiLineLabelUI.convertTabs(StringUtil.convertLineSeparators(this), 2)

}