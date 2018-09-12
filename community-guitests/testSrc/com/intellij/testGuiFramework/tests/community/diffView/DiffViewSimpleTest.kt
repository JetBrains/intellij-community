// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.diffView

import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import com.intellij.testGuiFramework.util.Key
import com.intellij.testGuiFramework.util.Modifier
import com.intellij.testGuiFramework.util.plus
import org.junit.Test

class DiffViewSimpleTest : GuiTestCase() {

  @Test
  fun testDiffViewInLoop() {
    CommunityProjectCreator.importCommandLineAppAndOpenFile("A.cpp")

    ideFrame {
      invokeMainMenu("Start.Use.Vcs")
      dialog("Enable Version Control Integration") {
        combobox("Select a version control system to associate with the project root:")
          .selectItem("Git")
        button("OK").click()
      }

      projectView {
        path("command-line-app", "src", "com.company", "A.cpp").doubleClick()
      }
      invokeMainMenu("Git.Add")

      println("typing code")
      editor {
        moveTo(0)
        typeText("abcdefg")
        shortcut(Key.ENTER)
        typeText("abcdefghij")
        shortcut(Key.ENTER)
      }

      commit("commit 1")

/*      println("adding file to git")
      dialog("Add File to Git") {
        button("Yes").click()
      }
*/
      editor {
        typeText("1234567")
        shortcut(Key.ENTER)
        typeText("1234567890")
        shortcut(Key.UP)
        shortcut(Key.UP)
        shortcut(Key.UP)
        typeText("hij")
        shortcut(Key.DOWN)
        typeText("klm")
      }

      commit("commit 2")

      invokeMainMenu("ActivateVersionControlToolWindow")

      for (i: Int in (1 .. 2)) {
        callDifView("commit 1")
        callDifView("commit 2")
      }

      invokeMainMenu("CloseProject")
    }
  }

  fun commit(commitMsg: String) {
    ideFrame {
      invokeMainMenu("CheckinFiles")
      dialog("Commit Changes") {
        editor {
          moveTo(7)
          shortcut(Modifier.META + Key.A)
          typeText(commitMsg)
          shortcut(Key.TAB)
          shortcut(Key.TAB)
          shortcut(Key.TAB)
          shortcut(Key.TAB)
          shortcut(Key.TAB)
          shortcut(Key.ENTER)
        }
      }
    }
  }

  fun callDifView(commitMsg: String) {
    ideFrame {
      toolwindow(id = "Version Control") {
        content(tabName = "Log") {
          table(commitMsg).cell(commitMsg).click()
          jTree("command-line-app,   1 file,   /private/var/folders/9z/hy4qh2y90s7f6s8grc1sv9gm0000gp/T/guiTest/command-line-app",
                "src/com/company,   1 file", "A.cpp").clickPath()
          jTree("command-line-app,   1 file,   /private/var/folders/9z/hy4qh2y90s7f6s8grc1sv9gm0000gp/T/guiTest/command-line-app",
                "src/com/company,   1 file", "A.cpp").rightClickPath()
          //menu("Show Diff").click()
          shortcut(Modifier.META + Key.D)
          Thread.sleep(2000)
          //invokeAction("EditorEscape")
          shortcut(Key.ESCAPE)
        }
      }
    }

  }
}
