// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.impl.gradleReimport
import com.intellij.testGuiFramework.impl.waitAMoment
import com.intellij.testGuiFramework.impl.waitForGradleReimport
import com.intellij.testGuiFramework.util.logInfo
import com.intellij.testGuiFramework.util.scenarios.openProjectStructureAndCheck
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogModel
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogScenarios
import org.junit.Test

class CreateKotlinMPProjectWebGuiTest : KotlinGuiTestCase() {

  /**
   * Kotlin Multiplatform - Web project available in Kotlin version started from 1.3
   * */
  override fun isIdeFrameRun(): Boolean {
    return if (versionFromPlugin.toString() >= "1.3") true
    else {
      logInfo("Project 'Kotlin Multiplatform - Web' is not available in the Kotlin version $versionFromPlugin")
      false
    }
  }

  @Test
  @JvmName("kotlin_mpp_web")
  fun createKotlinMppWebProject() {
    val extraTimeOut = 4000L
    val projectName = testMethod.methodName
    val kotlinVersion = KotlinTestProperties.kotlin_artifact_version
    if (!isIdeFrameRun()) return
    createKotlinMPProjectWeb(projectPath = projectFolder)

    waitAMoment(extraTimeOut)
    editSettingsGradle()
    editBuildGradle(
      kotlinVersion = kotlinVersion,
      isKotlinDslUsed = false
    )

    gradleReimport()
    waitForGradleReimport(projectName)
    waitAMoment()

    val expectedJars =
      (kotlinLibs[KotlinKind.Common]!!.kotlinMPProject.jars.getJars(kotlinVersion) +
       kotlinLibs[KotlinKind.JVM]!!.kotlinMPProject.jars.getJars(kotlinVersion) +
       kotlinLibs[KotlinKind.JS]!!.kotlinMPProject.jars.getJars(kotlinVersion))
        .toSet()

    projectStructureDialogScenarios.openProjectStructureAndCheck {
      projectStructureDialogModel.checkLibrariesFromMavenGradle(
        BuildSystem.Gradle,
        kotlinVersion,
        expectedJars
      )

      val commonFacet = defaultFacetSettings[TargetPlatform.Common]!!.copy(cmdParameters = "-Xmulti-platform")
      projectStructureDialogModel.checkFacetInOneModule(commonFacet, path = *arrayOf(projectName, "${projectName}_commonMain", "Kotlin"))
      projectStructureDialogModel.checkFacetInOneModule(commonFacet, path = *arrayOf(projectName, "${projectName}_commonTest", "Kotlin"))
      projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JavaScript]!!, path = *arrayOf(projectName, "${projectName}_jsMain", "Kotlin"))
      projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JavaScript]!!, path = *arrayOf(projectName, "${projectName}_jsTest", "Kotlin"))
      projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JVM16]!!, path = *arrayOf(projectName, "${projectName}_jvmMain", "Kotlin"))
      projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.JVM16]!!, path = *arrayOf(projectName, "${projectName}_jvmTest", "Kotlin"))
      projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.Common]!!, path = *arrayOf(projectName, "${projectName}_metadataMain", "Kotlin"))
      projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[TargetPlatform.Common]!!, path = *arrayOf(projectName, "${projectName}_metadataTest", "Kotlin"))
    }
  }

}