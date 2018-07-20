// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.impl.gradleReimport
import com.intellij.testGuiFramework.impl.waitAMoment
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel
import com.intellij.testGuiFramework.util.scenarios.openProjectStructureAndCheck
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogModel
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogScenarios
import org.junit.Test

class CreateKotlinMPProjectGuiTest : KotlinGuiTestCase() {
  @Test
  @JvmName("kotlin_mpp_common_root")
  fun createKotlinMppProjectCommonRoot() {
    val extraTimeOut = 4000L
    val projectName = testMethod.methodName
    val kotlinVersion = KotlinTestProperties.kotlin_artifact_version
    val setOfMPPModules = MPPModules.mppFullSet()
    createKotlinMPProject(
      projectPath = projectFolder,
      moduleName = projectName,
      mppProjectStructure = NewProjectDialogModel.MppProjectStructure.RootCommonModule,
      setOfMPPModules = MPPModules.mppFullSet()
    )
    waitAMoment(extraTimeOut)
    editSettingsGradle()
    editBuildGradle(
      kotlinVersion = kotlinVersion,
      isKotlinDslUsed = false
    )

    if (setOfMPPModules.contains(KotlinKind.JVM)) {
      editBuildGradle(kotlinVersion, false, "$projectName-jvm")
    }
    if (setOfMPPModules.contains(KotlinKind.JS)) {
      editBuildGradle(kotlinVersion, false, "$projectName-js")
    }
    gradleReimport()
    waitAMoment(extraTimeOut)

    val expectedJars = (kotlinLibs[KotlinKind.Common]!!.kotlinMPProject.jars.getJars(kotlinVersion) +
                        (if (setOfMPPModules.contains(
                            KotlinKind.JVM)) kotlinLibs[KotlinKind.JVM]!!.kotlinMPProject.jars.getJars(kotlinVersion) else emptyList()) +
                        (if (setOfMPPModules.contains(
                            KotlinKind.JS)) kotlinLibs[KotlinKind.JS]!!.kotlinMPProject.jars.getJars(kotlinVersion) else emptyList())
      ).toSet()

    projectStructureDialogScenarios.openProjectStructureAndCheck {
      projectStructureDialogModel.checkLibrariesFromMavenGradle(
        BuildSystem.Gradle,
        kotlinVersion,
        expectedJars
      )
      projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.Common]!!, "$projectName", "${projectName}_main", "Kotlin")
      projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.Common]!!, "$projectName", "${projectName}_test", "Kotlin")
      if (setOfMPPModules.contains(KotlinKind.JS)) {
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JavaScript]!!, "${projectName}-js", "${projectName}-js_main", "Kotlin")
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JavaScript]!!, "${projectName}-js", "${projectName}-js_test", "Kotlin")
      }
      if (setOfMPPModules.contains(KotlinKind.JVM)) {
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JVM18]!!, "${projectName}-jvm", "${projectName}-jvm_main", "Kotlin")
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JVM18]!!, "${projectName}-jvm", "${projectName}-jvm_test", "Kotlin")
      }
    }
  }

  @Test
  @JvmName("kotlin_mpp_empty_root")
  fun createKotlinMppProjectEmptyRoot() {
    val extraTimeOut = 4000L
    val projectName = testMethod.methodName
    val kotlinVersion = KotlinTestProperties.kotlin_artifact_version
    val setOfMPPModules = MPPModules.mppFullSet()
    createKotlinMPProject(
      projectPath = projectFolder,
      moduleName = projectName,
      mppProjectStructure = NewProjectDialogModel.MppProjectStructure.RootEmptyModule,
      setOfMPPModules = MPPModules.mppFullSet()
    )
    waitAMoment(extraTimeOut)
    editSettingsGradle()
    editBuildGradle(
      kotlinVersion = kotlinVersion,
      isKotlinDslUsed = false,
      projectName = *arrayOf("$projectName-common")
    )

    if (setOfMPPModules.contains(KotlinKind.JVM)) {
      editBuildGradle(kotlinVersion, false,  "$projectName-jvm")
    }
    if (setOfMPPModules.contains(KotlinKind.JS)) {
      editBuildGradle(kotlinVersion, false,  "$projectName-js")
    }
    gradleReimport()
    waitAMoment(extraTimeOut)

    val expectedJars = (kotlinLibs[KotlinKind.Common]!!.kotlinMPProject.jars.getJars(kotlinVersion) +
                        (if (setOfMPPModules.contains(
                            KotlinKind.JVM)) kotlinLibs[KotlinKind.JVM]!!.kotlinMPProject.jars.getJars(kotlinVersion) else emptyList()) +
                        (if (setOfMPPModules.contains(
                            KotlinKind.JS)) kotlinLibs[KotlinKind.JS]!!.kotlinMPProject.jars.getJars(kotlinVersion) else emptyList())
      ).toSet()

    projectStructureDialogScenarios.openProjectStructureAndCheck {
      projectStructureDialogModel.checkLibrariesFromMavenGradle(
        BuildSystem.Gradle,
        kotlinVersion,
        expectedJars
      )
      projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.Common]!!, "${projectName}-common", "${projectName}-common_main", "Kotlin")
      projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.Common]!!, "${projectName}-common", "${projectName}-common_test", "Kotlin")
      if (setOfMPPModules.contains(KotlinKind.JS)) {
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JavaScript]!!, "${projectName}-js", "${projectName}-js_main", "Kotlin")
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JavaScript]!!, "${projectName}-js", "${projectName}-js_test", "Kotlin")
      }
      if (setOfMPPModules.contains(KotlinKind.JVM)) {
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JVM18]!!, "${projectName}-jvm", "${projectName}-jvm_main", "Kotlin")
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JVM18]!!, "${projectName}-jvm", "${projectName}-jvm_test", "Kotlin")
      }
    }
  }
}