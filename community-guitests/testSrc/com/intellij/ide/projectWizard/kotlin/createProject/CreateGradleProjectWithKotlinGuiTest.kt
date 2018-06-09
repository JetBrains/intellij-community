// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.framework.GuiTestSuiteParam
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(GuiTestSuiteParam::class)
class CreateGradleProjectWithKotlinGuiTest(val testParameters: TestParameters) : KotlinGuiTestCase() {

  data class TestParameters(
    val projectName: String,
    val kotlinKind: KotlinKind,
    val project: ProjectProperties,
    val expectedFacet: FacetStructure) {
    override fun toString() = projectName
  }


  @Test
  fun createGradleWithKotlin() {
    createGradleWith(
      projectName = testParameters.projectName,
      kotlinKind = testParameters.kotlinKind,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = testParameters.project,
      expectedFacet = testParameters.expectedFacet)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<TestParameters> {
      return listOf(
        TestParameters(
          projectName = "gradle_with_jvm",
          kotlinKind = KotlinKind.JVM,
          project = kotlinLibs.getValue(KotlinKind.JVM).gradleGProject,
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JVM18)
        ),
        TestParameters(
          projectName = "gradle_mpp_jvm",
          kotlinKind = KotlinKind.JVM,
          project = kotlinLibs.getValue(KotlinKind.JVM).gradleGMPProject,
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JVM18)
        ),
        TestParameters(
          projectName = "gradle_with_js",
          kotlinKind = KotlinKind.JS,
          project = kotlinLibs.getValue(KotlinKind.JS).gradleGProject,
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JavaScript)
        ),
        TestParameters(
          projectName = "gradle_mpp_js",
          kotlinKind = KotlinKind.JS,
          project = kotlinLibs.getValue(KotlinKind.JS).gradleGMPProject,
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JavaScript)
        ),
        TestParameters(
          projectName = "gradle_mpp_common",
          kotlinKind = KotlinKind.Common,
          project = kotlinLibs.getValue(KotlinKind.Common).gradleGMPProject,
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.Common)
        )
      )
    }
  }

  private fun createGradleWith(
    projectName: String,
    kotlinKind: KotlinKind,
    kotlinVersion: String,
    project: ProjectProperties,
    expectedFacet: FacetStructure) {
    val groupName = "group_gradle"
    val extraTimeOut = 4000L
    createGradleProject(
      projectPath = projectFolder,
      group = groupName,
      artifact = projectName,
      gradleOptions = BuildGradleOptions().build(),
      framework = project.frameworkName)
    waitAMoment(extraTimeOut)
    editSettingsGradle()
    editBuildGradle(
      kotlinVersion = kotlinVersion,
      isKotlinDslUsed = false,
      kotlinKind = kotlinKind
    )
    gradleReimport()
    waitAMoment(extraTimeOut)

    checkInProjectStructureGradleExplicitModuleGroups(
      project, kotlinVersion, projectName, expectedFacet
    )
  }
}