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

import com.intellij.tests.gui.BelongsToTestGroups
import com.intellij.tests.gui.fixtures.WelcomeFrameFixture
import com.intellij.tests.gui.framework.GuiTestCase
import com.intellij.tests.gui.framework.TestGroup
import com.intellij.tests.gui.framework.createNewProject
import com.intellij.tests.gui.script.ScriptInterface
import org.junit.Assert

/**
 * Created by karashevich on 18/06/16.
 */
@BelongsToTestGroups(TestGroup.PROJECT)
class Script: GuiTestCase(), ScriptInterface {

  override fun script(){
    val createNewProject = createNewProject()
    val newProjectWizard = findNewProjectWizard()
    with(newProjectWizard) {
      clickCancel()
    }
    val find = WelcomeFrameFixture.find(myRobot)
    Assert.assertNotNull(find)
  }
}

