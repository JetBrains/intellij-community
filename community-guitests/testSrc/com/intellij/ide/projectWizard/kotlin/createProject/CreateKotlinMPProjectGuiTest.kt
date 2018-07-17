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
  @JvmName("kotlin_mpp_hierarchical")
  fun createKotlinMppProjectCommonRoot() {
    val extraTimeOut = 4000L
    val projectName = testMethod.methodName
    val kotlinVersion = KotlinTestProperties.kotlin_artifact_version
    val setOfMPPModules = MPPModules.mppFullSet()
    val module_common = "$projectName-common"
    val module_jvm = "$projectName-jvm"
    val module_js = "$projectName-js"
    createKotlinMPProject(
      projectPath = projectFolder,
      moduleName = projectName,
      mppProjectStructure = NewProjectDialogModel.MppProjectStructure.HierarchicalStructure,
      setOfMPPModules = MPPModules.mppFullSet()
    )
    waitAMoment(extraTimeOut)
    editSettingsGradle()
    editBuildGradle(
      kotlinVersion = kotlinVersion,
      isKotlinDslUsed = false,
      projectName = *arrayOf(module_common)
    )

    if (setOfMPPModules.contains(KotlinKind.JVM)) {
      editBuildGradle(kotlinVersion, false, module_common, module_jvm)
    }
    if (setOfMPPModules.contains(KotlinKind.JS)) {
      editBuildGradle(kotlinVersion, false, module_common, module_js)
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
      projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.Common]!!, path = *arrayOf(module_common, "${module_common}_main", "Kotlin"))
      projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.Common]!!, path = *arrayOf(module_common, "${module_common}_test", "Kotlin"))
      if (setOfMPPModules.contains(KotlinKind.JS)) {
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JavaScript]!!, path = *arrayOf(module_common, module_js, "${module_js}_main", "Kotlin"))
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JavaScript]!!, path = *arrayOf(module_common, module_js, "${module_js}_test", "Kotlin"))
      }
      if (setOfMPPModules.contains(KotlinKind.JVM)) {
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JVM18]!!, path = *arrayOf(module_common, module_jvm, "${module_jvm}_main", "Kotlin"))
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JVM18]!!, path = *arrayOf(module_common, module_jvm, "${module_jvm}_test", "Kotlin"))
      }
    }
  }

  @Test
  @JvmName("kotlin_mpp_flat")
  fun createKotlinMppProjectEmptyRoot() {
    val extraTimeOut = 4000L
    val projectName = testMethod.methodName
    val kotlinVersion = KotlinTestProperties.kotlin_artifact_version
    val setOfMPPModules = MPPModules.mppFullSet()
    val module_common = "$projectName-common"
    val module_jvm = "$projectName-jvm"
    val module_js = "$projectName-js"
    createKotlinMPProject(
      projectPath = projectFolder,
      moduleName = projectName,
      mppProjectStructure = NewProjectDialogModel.MppProjectStructure.FlatStructure,
      setOfMPPModules = MPPModules.mppFullSet()
    )
    waitAMoment(extraTimeOut)
    editSettingsGradle()
    editBuildGradle(
      kotlinVersion = kotlinVersion,
      isKotlinDslUsed = false,
      projectName = *arrayOf(module_common)
    )

    if (setOfMPPModules.contains(KotlinKind.JVM)) {
      editBuildGradle(kotlinVersion, false,  module_jvm)
    }
    if (setOfMPPModules.contains(KotlinKind.JS)) {
      editBuildGradle(kotlinVersion, false,  module_js)
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
      projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.Common]!!, path = *arrayOf(module_common, "${module_common}_main", "Kotlin"))
      projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.Common]!!, path = *arrayOf(module_common, "${module_common}_test", "Kotlin"))
      if (setOfMPPModules.contains(KotlinKind.JS)) {
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JavaScript]!!, path = *arrayOf(module_js, "${module_js}_main", "Kotlin"))
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JavaScript]!!, path = *arrayOf(module_js, "${module_js}_test", "Kotlin"))
      }
      if (setOfMPPModules.contains(KotlinKind.JVM)) {
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JVM18]!!, path = *arrayOf(module_jvm, "${module_jvm}_main", "Kotlin"))
        projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JVM18]!!, path = *arrayOf(module_jvm, "${module_jvm}_test", "Kotlin"))
      }
    }
  }
}