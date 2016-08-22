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
import com.intellij.tests.gui.BelongsToTestGroups
import com.intellij.tests.gui.fixtures.IdeaDialogFixture
import com.intellij.tests.gui.fixtures.JBPopupFixture
import com.intellij.tests.gui.fixtures.WelcomeFrameFixture
import com.intellij.tests.gui.framework.GuiTestCase
import com.intellij.tests.gui.framework.GuiTests
import com.intellij.tests.gui.framework.TestGroup
import org.fest.swing.fixture.DialogFixture
import org.fest.swing.fixture.JTextComponentFixture
import org.junit.Test
import javax.swing.text.JTextComponent

/**
 * Created by karashevich on 18/06/16.
 */
@BelongsToTestGroups(TestGroup.PROJECT)
class GitTest : GuiTestCase() {

  @Test
  fun testGitImport(){
    val vcsName = "Git"
    val gitPath = "https://github.com/karashevich/test.git"

    WelcomeFrameFixture.find(myRobot).checkoutFrom()
    JBPopupFixture.findContextMenu(myRobot).invokeAction(vcsName)

    val cloneVcsDialog = DialogFixture(IdeaDialogFixture.find(myRobot, CloneDvcsDialog::class.java).dialog)
    val labelText = DvcsBundle.message("clone.repository.url", vcsName)
    val urlTextField = JTextComponentFixture(myRobot, myRobot.finder().findByLabel(labelText, JTextComponent::class.java))
    urlTextField.setText(gitPath)
    GuiTests.findAndClickButton(cloneVcsDialog, DvcsBundle.getString("clone.button"))

  }
}

