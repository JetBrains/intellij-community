// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.impl.gradleReimport
import com.intellij.testGuiFramework.impl.waitAMoment
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogScenarios
import org.junit.Test

class CreateGradleKotlinDslProjectWithKotlinGuiTest : KotlinGuiTestCase() {
  @Test
  @JvmName("gradle_k_with_jvm")
  fun createGradleWithKotlinJvm() {
    createGradleWith(
      projectName = testMethod.methodName,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinLibs[KotlinKind.JVM]!!.gradleKProject,
      expectedFacet = defaultFacetSettings[TargetPlatform.JVM18]!!)
  }

  @Test
  @JvmName("gradle_k_with_js")
  fun createGradleWithKotlinJs() {
    createGradleWith(
      projectName = testMethod.methodName,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinLibs[KotlinKind.JS]!!.gradleKProject,
      expectedFacet = defaultFacetSettings[TargetPlatform.JavaScript]!!)
  }

   private fun createGradleWith(
     projectName: String,
     kotlinVersion: String,
     project: ProjectProperties,
     expectedFacet: FacetStructure) {
    val groupName = "group_gradle"
    val extraTimeOut = 4000L
    createGradleProject(
      projectPath = projectFolder,
      gradleOptions = NewProjectDialogModel.GradleProjectOptions(
        group = groupName,
        artifact = projectName,
        useKotlinDsl = true,
        framework = project.frameworkName
      )
    )
    waitAMoment(extraTimeOut)
    waitAMoment(extraTimeOut)
    editSettingsGradle()
    editBuildGradle(
      kotlinVersion = kotlinVersion,
      isKotlinDslUsed = true
    )
    gradleReimport()
    waitAMoment(extraTimeOut)

     projectStructureDialogScenarios.checkGradleExplicitModuleGroups(
       project, kotlinVersion, projectName, expectedFacet
     )
  }
}