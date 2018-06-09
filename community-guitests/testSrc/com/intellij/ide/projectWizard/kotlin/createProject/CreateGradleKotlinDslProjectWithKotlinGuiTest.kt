// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import org.junit.Test

class CreateGradleKotlinDslProjectWithKotlinGuiTest : KotlinGuiTestCase() {
  @Test
  @JvmName("gradle_k_with_jvm")
  fun createGradleWithKotlinJvm() {
    createGradleWith(
      projectName = testMethod.methodName,
      kotlinKind = KotlinKind.JVM,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinLibs[KotlinKind.JVM]!!.gradleKProject,
      expectedFacet = defaultFacetSettings[TargetPlatform.JVM18]!!)
  }

  @Test
  @JvmName("gradle_k_with_js")
  fun createGradleWithKotlinJs() {
    createGradleWith(
      projectName = testMethod.methodName,
      kotlinKind = KotlinKind.JS,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinLibs[KotlinKind.JS]!!.gradleKProject,
      expectedFacet = defaultFacetSettings[TargetPlatform.JavaScript]!!)
  }

   fun createGradleWith(
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
      framework = project.frameworkName,
      isJavaUsed = true,
      isKotlinDslUsed = true)
    waitAMoment(extraTimeOut)
    editSettingsGradle()
    editBuildGradle(
      kotlinVersion = kotlinVersion,
      isKotlinDslUsed = true,
      kotlinKind = kotlinKind
    )
    gradleReimport()
    waitAMoment(extraTimeOut)

     checkInProjectStructureGradleExplicitModuleGroups(
       project, kotlinVersion, projectName, expectedFacet
     )
  }
}