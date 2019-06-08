// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.framework.param.GuiTestSuiteParam
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.Serializable

@RunWith(GuiTestSuiteParam::class)
class CreateGradleProjectWithKotlinGuiTest(private val testParameters: TestParameters) : KotlinGuiTestCase() {

  data class TestParameters(
    val projectName: String,
    val project: ProjectProperties,
    val gradleModuleGroup: NewProjectDialogModel.GradleGroupModules,
    val expectedFacet: FacetStructure) : Serializable {
    override fun toString() = projectName
  }

  @Before
  fun beforeTest(){
    screenshot("before")
  }

  @After
  fun afterTest(){
    screenshot("after")
  }

  @Test
  fun createGradleWithKotlin() {
    testGradleProjectWithKotlin(
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = testParameters.project,
      expectedFacet = testParameters.expectedFacet,
      gradleOptions = NewProjectDialogModel.GradleProjectOptions(
        artifact = testParameters.projectName,
        framework = testParameters.project.frameworkName,
        useKotlinDsl = testParameters.project.isKotlinDsl,
        groupModules = testParameters.gradleModuleGroup
      )
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<TestParameters> {
      return listOf(
        TestParameters(
          projectName = "gradle_with_jvm_qualified",
          project = kotlinProjects.getValue(Projects.GradleGProjectJvm),
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JVM18),
          gradleModuleGroup = NewProjectDialogModel.GradleGroupModules.QualifiedNames
        ),
        TestParameters(
          projectName = "gradle_with_js_qualified",
          project = kotlinProjects.getValue(Projects.GradleGProjectJs),
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JavaScript),
          gradleModuleGroup = NewProjectDialogModel.GradleGroupModules.QualifiedNames
        )
      )
    }
  }

}