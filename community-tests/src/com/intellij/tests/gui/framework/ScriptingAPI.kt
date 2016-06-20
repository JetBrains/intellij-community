package com.intellij.tests.gui.framework

import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.tests.gui.fixtures.WelcomeFrameFixture
import org.fest.swing.core.Robot
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause

/**
    We presume that IDEA is already started, shows "Welcome dialog" and we could create a new project
**/


fun GuiTestCase.createNewProject(): WelcomeFrameFixture {
  return findWelcomeFrame().createNewProject()
}

fun pause(time: Long, root: Robot) {
  Pause.pause(object : Condition("Wait for user actions") {
    override fun test(): Boolean {
      return false
    }
  }, time)

}