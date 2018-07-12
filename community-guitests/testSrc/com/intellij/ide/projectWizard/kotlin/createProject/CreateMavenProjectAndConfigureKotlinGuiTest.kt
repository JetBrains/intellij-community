// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.impl.mavenReimport
import com.intellij.testGuiFramework.impl.waitAMoment
import com.intellij.testGuiFramework.util.*
import com.intellij.testGuiFramework.util.scenarios.openProjectStructureAndCheck
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogModel
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogScenarios
import org.junit.Test

class CreateMavenProjectAndConfigureKotlinGuiTest : KotlinGuiTestCase() {

  @Test
  @JvmName("maven_cfg_jvm")
  fun createMavenAndConfigureKotlinJvm() {
    val projectName = testMethod.methodName
    val kotlinVersion = KotlinTestProperties.kotlin_artifact_version
    val kotlinKind = KotlinKind.JVM
    val extraTimeOut = 4000L
    if (!isIdeFrameRun()) return
    createMavenProject(
      projectPath = projectFolder,
      artifact = projectName)
    waitAMoment(extraTimeOut)
    configureKotlinJvmFromMaven(kotlinVersion)
    waitAMoment(extraTimeOut)
    saveAndCloseCurrentEditor()
    editPomXml(
      kotlinVersion = kotlinVersion,
      kotlinKind = kotlinKind
    )
    waitAMoment(extraTimeOut)
    mavenReimport()
    waitAMoment(extraTimeOut)

    projectStructureDialogScenarios.openProjectStructureAndCheck {
      projectStructureDialogModel.checkLibrariesFromMavenGradle(
        buildSystem = BuildSystem.Maven,
        kotlinVersion = kotlinVersion,
        expectedJars = kotlinLibs[kotlinKind]!!.mavenProject.jars.getJars(kotlinVersion)
      )
      projectStructureDialogModel.checkFacetInOneModule(
        defaultFacetSettings[TargetPlatform.JVM18]!!,
        projectName, "Kotlin"
      )
    }
  }

  @Test
  @JvmName("maven_cfg_js")
  fun createMavenAndConfigureKotlinJs() {
    val projectName = testMethod.methodName
    val kotlinVersion = KotlinTestProperties.kotlin_artifact_version
    val kotlinKind = KotlinKind.JS
    val extraTimeOut = 4000L
    if (!isIdeFrameRun()) return
    createMavenProject(
      projectPath = projectFolder,
      artifact = projectName)
    waitAMoment(extraTimeOut)
    configureKotlinJsFromMaven(kotlinVersion)
    waitAMoment(extraTimeOut)
    saveAndCloseCurrentEditor()
    editPomXml(
      kotlinVersion = kotlinVersion,
      kotlinKind = kotlinKind
    )
    waitAMoment(extraTimeOut)
    mavenReimport()
    waitAMoment(extraTimeOut)

    projectStructureDialogScenarios.openProjectStructureAndCheck {
      projectStructureDialogModel.checkLibrariesFromMavenGradle(
        buildSystem = BuildSystem.Maven,
        kotlinVersion = kotlinVersion,
        expectedJars = kotlinLibs[kotlinKind]!!.mavenProject.jars.getJars(kotlinVersion)
      )
      projectStructureDialogModel.checkFacetInOneModule(
        defaultFacetSettings[TargetPlatform.JavaScript]!!,
        projectName, "Kotlin"
      )
    }
  }

  override fun isIdeFrameRun(): Boolean =
      if (KotlinTestProperties.isActualKotlinUsed() && !KotlinTestProperties.isArtifactPresentInConfigureDialog) {
        logInfo("The tested artifact ${KotlinTestProperties.kotlin_artifact_version} is not present in the configuration dialog. This is not a bug, but the test '${testMethod.methodName}' is skipped (though marked as passed)")
        false
      }
      else true

  /*@Test
  @Ignore
  @JvmName("maven_jvm_from_file")
  fun createMavenAndConfigureKotlinJvmFromFile() {
    val groupName = "group_maven_jvm"
    val artifactName = "art_maven_jvm"
    createMavenProject(
        projectPath = projectFolder.absolutePath,
        group = groupName,
        artifact = artifactName)
    waitAMoment(10000)
//    configureKotlinJvm(libInPlugin = false)
    createKotlinFile(
        projectName = testMethod.methodName,
        packageName = "src/main/java",
        fileName = "K1"
    )
    waitAMoment(10000)
  }*/
}