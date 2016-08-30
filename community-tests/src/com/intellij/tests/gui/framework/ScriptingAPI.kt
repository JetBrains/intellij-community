package com.intellij.tests.gui.framework

import com.intellij.tests.gui.fixtures.WelcomeFrameFixture
import com.intellij.ui.components.JBList
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JListFixture
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import java.awt.Container

/**
    We presume that IDEA is already started, shows "Welcome dialog" and we could create a new project

 !!UNFINISHED!!
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

fun clickListItem(itemName: String, robot: Robot, parentContainer: Container) {
  val listFixture = JListFixture(robot, robot.finder().findByType(parentContainer, JBList::class.java, true))
  listFixture.clickItem(itemName)
}