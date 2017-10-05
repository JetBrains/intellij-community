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
package com.intellij.testGuiFramework.tests.community

import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.util.Key
import com.intellij.testGuiFramework.util.Modifier
import com.intellij.testGuiFramework.util.plus
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test

/**
 * @author Sergey Karashevich
 */
@RunWithIde(CommunityIde::class)
class ColorSchemeTest: GuiTestCase() {

  @Test
  fun testColorScheme() {
    val importSimpleProject = guiTestRule.importSimpleProject()

    ideFrame {
      //invoke an action "ShowSettings" via keystroke string
      waitForBackgroundTasksToFinish()
      shortcut(Modifier.META + Key.COMMA)
      dialog("Preferences") {
        jTree("Editor", "Colors & Fonts", "Java").clickPath("Editor", "Colors & Fonts", "Java")
        org.fest.swing.timing.Pause.pause(1000)
        jTree("Keyword").clickPath("Keyword")
        checkbox("Use inherited attributes")
        button("OK").click()
      }
    }

  }

  @Test
  @Ignore
  fun testColorScheme2() {
    ideFrame {
      //invoke an action "ShowSettings" via keystroke string
      waitForBackgroundTasksToFinish()
      shortcut("meta comma")
      dialog("Preferences") {
        jTree("Editor", "Colors & Fonts", "Java").clickPath("Editor", "Colors & Fonts", "Java")
        org.fest.swing.timing.Pause.pause(1000)
        jTree("Keyword").clickPath("Keyword")
        checkbox("Use inherited attributes")
        button("OK").click()
      }
    }

  }

  @Test
  @Ignore
  fun testFail() {
    welcomeFrame {
      fail()
    }
  }

  private fun createProject() {
    welcomeFrame{

    }
  }
}