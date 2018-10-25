// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.framework.param.GuiTestSuiteParam
import com.intellij.testGuiFramework.impl.gradleReimport
import com.intellij.testGuiFramework.impl.waitAMoment
import com.intellij.testGuiFramework.impl.waitForGradleReimport
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogScenarios
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.Serializable

@RunWith(GuiTestSuiteParam::class)
class CreateGradleProjectWithKotlinGuiTest(private val testParameters: TestParameters) : KotlinGuiTestCase() {

  data class TestParameters(
    val projectName: String,
    val project: ProjectProperties,
    val expectedFacet: FacetStructure) : Serializable {
    override fun toString() = projectName
  }

  @Test
  fun createGradleWithKotlin() {
    createGradleWith(
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = testParameters.project,
      expectedFacet = testParameters.expectedFacet,
      gradleOptions = NewProjectDialogModel.GradleProjectOptions(
        artifact = testParameters.projectName,
        framework = testParameters.project.frameworkName,
        useKotlinDsl = testParameters.project.isKotlinDsl
      )
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<TestParameters> {
      return listOf(
        TestParameters(
          projectName = "gradle_with_jvm",
          project = kotlinProjects.getValue(Projects.GradleGProjectJvm),
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JVM18)
        ),
        TestParameters(
          projectName = "gradle_with_js",
          project = kotlinProjects.getValue(Projects.GradleGProjectJs),
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JavaScript)
        )
      )
    }
  }

  private fun createGradleWith(
    kotlinVersion: String,
    project: ProjectProperties,
    expectedFacet: FacetStructure,
    gradleOptions: NewProjectDialogModel.GradleProjectOptions) {
    createGradleProject(
      projectPath = projectFolder,
      gradleOptions = gradleOptions
    )
    waitAMoment()
    waitForGradleReimport(gradleOptions.artifact, waitForProject = false)
    editSettingsGradle()
    editBuildGradle(
      kotlinVersion = kotlinVersion,
      isKotlinDslUsed = gradleOptions.useKotlinDsl
    )
    gradleReimport()
    waitForGradleReimport(gradleOptions.artifact, waitForProject = true)
    waitAMoment()

    projectStructureDialogScenarios.checkGradleExplicitModuleGroups(
      project, kotlinVersion, gradleOptions.artifact, expectedFacet
    )
    waitAMoment()
  }
}