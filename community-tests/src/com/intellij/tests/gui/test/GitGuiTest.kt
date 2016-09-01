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
package com.intellij.tests.gui.test

import com.intellij.dvcs.ui.CloneDvcsDialog
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.tests.gui.BelongsToTestGroups
import com.intellij.tests.gui.fixtures.*
import com.intellij.tests.gui.framework.GuiTestCase
import com.intellij.tests.gui.framework.GuiTests
import com.intellij.tests.gui.framework.TestGroup
import com.intellij.ui.EditorComboBox
import org.fest.swing.edt.GuiActionRunner
import org.fest.swing.edt.GuiTask
import org.fest.swing.fixture.DialogFixture
import org.junit.Test

/**
 * Created by karashevich on 18/06/16.
 */
@BelongsToTestGroups(TestGroup.PROJECT)
class GitGuiTest : GuiTestCase() {

  @Test
  fun testGitImport(){
    val vcsName = "Git"
    val gitPath = "https://github.com/karashevich/test.git"

    val welcomeFrame = WelcomeFrameFixture.find(myRobot)
    welcomeFrame.checkoutFrom()
    JBListPopupFixture.findListPopup(myRobot).invokeAction(vcsName)

    val cloneVcsDialog = DialogFixture(myRobot, IdeaDialogFixture.find(myRobot, CloneDvcsDialog::class.java).dialog) //don't miss robot as the first argument or you'll stuck with a deadlock
    with(cloneVcsDialog) {
      val labelText = DvcsBundle.message("clone.repository.url", vcsName)
      val editorComboBox = myRobot.finder().findByLabel(this.target(), labelText, EditorComboBox::class.java)
      GuiActionRunner.execute(object : GuiTask() {
        @Throws(Throwable::class)
        override fun executeInEDT() {
          editorComboBox.text = gitPath
        }
      })
      GuiTests.findAndClickButton(this, DvcsBundle.getString("clone.button"))
    }
    MessagesFixture.findByTitle(myRobot, welcomeFrame.target(), VcsBundle.message("checkout.title")).clickYes()
    val dialog1 = com.intellij.tests.gui.fixtures.DialogFixture.find(myRobot, "Import Project")
    with (dialog1) {
      GuiTests.findAndClickButton(this, "Next")
      val textField = GuiTests.findTextField(myRobot, "Project name:").click()
      GuiTests.findAndClickButton(this, "Next")
      GuiTests.findAndClickButton(this, "Next")
      GuiTests.findAndClickButton(this, "Finish")
    }
    val ideFrame = findIdeFrame()
    val projectView = ideFrame.projectView
    val paneFixture = projectView.selectProjectPane()

    ToolWindowFixture.showToolwindowStripes(myRobot)

    //prevent from ProjectLeak (if the project is closed during the indexing
    DumbService.getInstance(ideFrame.project).waitForSmartMode()

  }
}

