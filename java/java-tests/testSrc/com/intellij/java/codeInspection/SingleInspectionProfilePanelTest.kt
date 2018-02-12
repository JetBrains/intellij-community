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
package com.intellij.java.codeInspection

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionProfileTest
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection
import com.intellij.openapi.project.ProjectManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.testFramework.configureInspections
import com.intellij.testFramework.createProfile
import com.intellij.testFramework.runInInitMode
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat

class SingleInspectionProfilePanelTest : LightIdeaTestCase() {
  private val myInspection = JavaDocLocalInspection()

  // see IDEA-85700
  fun testSettingsModification() {
    runInInitMode {
      val project = ProjectManager.getInstance().defaultProject
      val profile = configureInspections(arrayOf(myInspection), project, testRootDisposable)

      val model = profile.modifiableModel
      val panel = SingleInspectionProfilePanel(ProjectInspectionProfileManager.getInstance(project), model)
      panel.isVisible = true
      panel.reset()

      val tool = getInspection(model)
      assertEquals("", tool.myAdditionalJavadocTags)
      tool.myAdditionalJavadocTags = "foo"
      model.setModified(true)
      panel.apply()
      assertThat(InspectionProfileTest.countInitializedTools(model)).isEqualTo(1)

      assertThat(getInspection(profile).myAdditionalJavadocTags).isEqualTo("foo")
      panel.disposeUI()
    }
  }

  fun testModifyInstantiatedTool() {
    val project = ProjectManager.getInstance().defaultProject
    val profileManager = ProjectInspectionProfileManager.getInstance(project)
    val profile = profileManager.createProfile(myInspection, testRootDisposable)
    profile.initInspectionTools(project)

    val originalTool = getInspection(profile)
    originalTool.myAdditionalJavadocTags = "foo"

    val model = profile.modifiableModel

    val panel = SingleInspectionProfilePanel(profileManager, model)
    panel.isVisible = true
    panel.reset()
    TestCase.assertEquals(InspectionProfileTest.getInitializedTools(model).toString(), 1,
                          InspectionProfileTest.countInitializedTools(model))

    val copyTool = getInspection(model)
    copyTool.myAdditionalJavadocTags = "bar"

    model.setModified(true)
    panel.apply()
    assertThat(InspectionProfileTest.countInitializedTools(model)).isEqualTo(1)

    assertEquals("bar", getInspection(profile).myAdditionalJavadocTags)
    panel.disposeUI()
  }

  fun testDoNotChangeSettingsOnCancel() {
    val project = ProjectManager.getInstance().defaultProject
    val profileManager = ProjectInspectionProfileManager.getInstance(project)
    val profile = profileManager.createProfile(myInspection, testRootDisposable)
    profile.initInspectionTools(project)

    val originalTool = getInspection(profile)
    assertThat(originalTool.myAdditionalJavadocTags).isEmpty()

    val model = profile.modifiableModel
    val copyTool = getInspection(model)
    copyTool.myAdditionalJavadocTags = "foo"
    // this change IS NOT COMMITTED

    assertEquals("", getInspection(profile).myAdditionalJavadocTags)
  }

  private fun getInspection(profile: InspectionProfileImpl): JavaDocLocalInspection {
    return (profile.getInspectionTool(myInspection.shortName, getProject()) as LocalInspectionToolWrapper?)!!.tool as JavaDocLocalInspection
  }

  override fun setUp() {
    InspectionProfileImpl.INIT_INSPECTIONS = true
    super.setUp()
  }

  override fun tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false
    super.tearDown()
  }

  override fun configureLocalInspectionTools() = arrayOf(myInspection)
}
