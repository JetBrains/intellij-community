// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import org.junit.Test

class CreateGradleProjectWithKotlinGuiTest : KotlinGuiTestCase() {
  @Test
  @JvmName("gradle_with_jvm")
  fun createGradleWithKotlinJvm() {
    createGradleWith(
      projectName = testMethod.methodName,
      kotlinKind = KotlinKind.JVM,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinLibs[KotlinKind.JVM]!!.gradleGProject,
      expectedFacet = defaultFacetSettings[TargetPlatform.JVM18]!!)
  }

  @Test
  @JvmName("gradle_with_js")
  fun createGradleWithKotlinJs() {
    createGradleWith(
      projectName = testMethod.methodName,
      kotlinKind = KotlinKind.JS,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinLibs[KotlinKind.JS]!!.gradleGProject,
      expectedFacet = defaultFacetSettings[TargetPlatform.JavaScript]!!)
  }

  @Test
  @JvmName("gradle_mpp_common")
   fun createGradleMppCommon(){
    createGradleWith(
      projectName = testMethod.methodName,
      kotlinKind = KotlinKind.Common,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinLibs[KotlinKind.Common]!!.gradleGMPProject,
      expectedFacet = defaultFacetSettings[TargetPlatform.Common]!!)
  }

  @Test
  @JvmName("gradle_mpp_jvm")
   fun createGradleMppJvm(){
    createGradleWith(
      projectName = testMethod.methodName,
      kotlinKind = KotlinKind.JVM,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinLibs[KotlinKind.JVM]!!.gradleGMPProject,
      expectedFacet = defaultFacetSettings[TargetPlatform.JVM18]!!)
  }

  @Test
  @JvmName("gradle_mpp_js")
   fun createGradleMppJs(){
    createGradleWith(
      projectName = testMethod.methodName,
      kotlinKind = KotlinKind.JS,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinLibs[KotlinKind.JS]!!.gradleGMPProject,
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
      framework = project.frameworkName)
    waitAMoment(extraTimeOut)
    editBuildGradle(
      kotlinVersion = kotlinVersion,
      isKotlinDslUsed = false,
      kotlinKind = kotlinKind,
      projectName = *arrayOf(projectName)
    )
    gradleReimport()
    waitAMoment(extraTimeOut)

     checkInProjectStructureGradleExplicitModuleGroups(
       project, kotlinVersion, projectName, expectedFacet
     )
  }
}