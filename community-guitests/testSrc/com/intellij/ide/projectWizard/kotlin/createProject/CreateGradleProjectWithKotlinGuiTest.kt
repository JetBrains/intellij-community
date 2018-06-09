// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.framework.GuiTestSuiteParam
import com.intellij.testGuiFramework.impl.gradleReimport
import com.intellij.testGuiFramework.impl.waitAMoment
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogScenarios
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(GuiTestSuiteParam::class)
class CreateGradleProjectWithKotlinGuiTest(val testParameters: TestParameters) : KotlinGuiTestCase() {

  data class TestParameters(
    val projectName: String,
    val project: ProjectProperties,
    val expectedFacet: FacetStructure) {
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
        framework = testParameters.project.frameworkName
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
          project = kotlinLibs.getValue(KotlinKind.JVM).gradleGProject,
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JVM18)
        ),
        TestParameters(
          projectName = "gradle_mpp_jvm",
          project = kotlinLibs.getValue(KotlinKind.JVM).gradleGMPProject,
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JVM18)
        ),
        TestParameters(
          projectName = "gradle_with_js",
          project = kotlinLibs.getValue(KotlinKind.JS).gradleGProject,
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JavaScript)
        ),
        TestParameters(
          projectName = "gradle_mpp_js",
          project = kotlinLibs.getValue(KotlinKind.JS).gradleGMPProject,
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JavaScript)
        ),
        TestParameters(
          projectName = "gradle_mpp_common",
          project = kotlinLibs.getValue(KotlinKind.Common).gradleGMPProject,
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.Common)
        )
      )
    }
  }

  private fun createGradleWith(
    kotlinVersion: String,
    project: ProjectProperties,
    expectedFacet: FacetStructure,
    gradleOptions: NewProjectDialogModel.GradleProjectOptions) {
    val extraTimeOut = 4000L
    createGradleProject(
      projectPath = projectFolder,
      gradleOptions = gradleOptions
    )
    waitAMoment(extraTimeOut)
    editSettingsGradle()
    editBuildGradle(
      kotlinVersion = kotlinVersion,
      isKotlinDslUsed = false
    )
    gradleReimport()
    waitAMoment(extraTimeOut)

    projectStructureDialogScenarios.checkGradleExplicitModuleGroups(
      project, kotlinVersion, gradleOptions.artifact, expectedFacet
    )
  }
}