// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection
import com.intellij.java.codeInspection.ex.InspectionProfileTest
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.ProjectManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.configureInspections
import com.intellij.testFramework.createProfile
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInInitMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class SingleInspectionProfilePanelTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(DefaultLightProjectDescriptor())
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @TestDisposable
  lateinit var testRootDisposable: Disposable

  private val myInspection = JavadocDeclarationInspection()

  @BeforeEach
  fun setUp() {
    InspectionProfileImpl.INIT_INSPECTIONS = true
    myFixture.enableInspections(myInspection)
  }

  @AfterEach
  fun tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false
  }

  // see IDEA-85700
  @Test
  fun testSettingsModification(): Unit = timeoutRunBlocking {
    runInInitMode {
      val project = ProjectManager.getInstance().defaultProject
      val profile = configureInspections(arrayOf(myInspection), project, testRootDisposable)

      val model = profile.modifiableModel
      withContext(Dispatchers.EDT) {
        val panel = SingleInspectionProfilePanel(ProjectInspectionProfileManager.getInstance(project), model)
        panel.isVisible = true
        panel.reset()

        val tool = getInspection(model)
        assertEquals("", tool.ADDITIONAL_TAGS)
        tool.ADDITIONAL_TAGS = "foo"
        model.setModified(true)
        panel.apply()
        assertThat(InspectionProfileTest.countInitializedTools(model)).isEqualTo(1)

        assertThat(getInspection(profile).ADDITIONAL_TAGS).isEqualTo("foo")
        panel.disposeUI()
      }
    }
  }

  @Test
  fun testModifyInstantiatedTool(): Unit = timeoutRunBlocking {
    val project = ProjectManager.getInstance().defaultProject
    val profileManager = ProjectInspectionProfileManager.getInstance(project)
    val profile = profileManager.createProfile(myInspection, testRootDisposable)
    profile.initInspectionTools(project)

    val originalTool = getInspection(profile)
    originalTool.ADDITIONAL_TAGS = "foo"

    val model = profile.modifiableModel

    withContext(Dispatchers.EDT) {
      val panel = SingleInspectionProfilePanel(profileManager, model)
      panel.isVisible = true
      panel.reset()
      assertEquals(1, InspectionProfileTest.countInitializedTools(model),
                   InspectionProfileTest.getInitializedTools(model).toString())

      val copyTool = getInspection(model)
      copyTool.ADDITIONAL_TAGS = "bar"

      model.setModified(true)
      panel.apply()
      assertThat(InspectionProfileTest.countInitializedTools(model)).isEqualTo(1)

      assertEquals("bar", getInspection(profile).ADDITIONAL_TAGS)
      panel.disposeUI()
    }
  }

  @Test
  fun testDoNotChangeSettingsOnCancel() {
    val project = ProjectManager.getInstance().defaultProject
    val profileManager = ProjectInspectionProfileManager.getInstance(project)
    val profile = profileManager.createProfile(myInspection, testRootDisposable)
    profile.initInspectionTools(project)

    val originalTool = getInspection(profile)
    assertThat(originalTool.ADDITIONAL_TAGS).isEmpty()

    val model = profile.modifiableModel
    val copyTool = getInspection(model)
    copyTool.ADDITIONAL_TAGS = "foo"
    // this change IS NOT COMMITTED

    assertEquals("", getInspection(profile).ADDITIONAL_TAGS)
  }

  private fun getInspection(profile: InspectionProfileImpl): JavadocDeclarationInspection {
    return (profile.getInspectionTool(myInspection.shortName, myFixture.project) as LocalInspectionToolWrapper?)!!.tool as JavadocDeclarationInspection
  }
}
