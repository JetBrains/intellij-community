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

class CreateMavenProjectWithKotlinGuiTest : KotlinGuiTestCase() {
  @Test
  @JvmName("maven_with_jvm")
  fun createMavenWithKotlinJvm() {
    val projectName = testMethod.methodName
    val kotlinVersion = KotlinTestProperties.kotlin_artifact_version
    if (!isIdeFrameRun()) return
    createMavenProject(
      projectPath = projectFolder,
      artifact = projectName,
      archetype = kotlinProjects.getValue(Projects.MavenProjectJvm).frameworkName,
      kotlinVersion = kotlinVersion)
    waitAMoment()
    mavenReimport()
    // TODO: remove extra mavenReimport after GUI-72 fixing
    waitAMoment()
    mavenReimport()
    waitAMoment()

    projectStructureDialogScenarios.openProjectStructureAndCheck {
      projectStructureDialogModel.checkLibrariesFromMavenGradle(
        buildSystem = BuildSystem.Maven,
        kotlinVersion = kotlinVersion,
        // TODO: use default set after fix KT-21230
        expectedJars = listOf(
          "org.jetbrains.kotlin:kotlin-stdlib:",
          "org.jetbrains.kotlin:kotlin-test:",
          "org.jetbrains:annotations:13.0"
        )
      )
      projectStructureDialogModel.checkFacetInOneModule(
        defaultFacetSettings[TargetPlatform.JVM16]!!,
        projectName, "Kotlin"
      )
    }

  }

  @Test
  @JvmName("maven_with_js")
  fun createMavenWithKotlinJs() {
    val projectName = testMethod.methodName
    val kotlinVersion = KotlinTestProperties.kotlin_artifact_version
    if (!isIdeFrameRun()) return
    createMavenProject(
      projectPath = projectFolder,
      artifact = projectName,
      archetype = kotlinProjects.getValue(Projects.MavenProjectJs).frameworkName,
      kotlinVersion = kotlinVersion)
    waitAMoment()
    mavenReimport()
    // TODO: remove extra mavenReimport after GUI-72 fixing
    waitAMoment()
    mavenReimport()
    waitAMoment()

    val expectedOutput = "$projectFolder/target/classes/${testMethod.methodName}.js".replace("\\", "/")
    val expectedFacet = defaultFacetSettings[TargetPlatform.JavaScript]!!.copy(
      cmdParameters = "-output $expectedOutput",
      jsOptions = defaultFacetSettings[TargetPlatform.JavaScript]!!.jsOptions?.copy(
        generateSourceMap = true
      )
    )

    projectStructureDialogScenarios.openProjectStructureAndCheck {
      projectStructureDialogModel.checkLibrariesFromMavenGradle(
        buildSystem = BuildSystem.Maven,
        kotlinVersion = kotlinVersion,
        expectedJars = kotlinProjects.getValue(Projects.MavenProjectJs).jars.getJars(kotlinVersion)
      )
      projectStructureDialogModel.checkFacetInOneModule(
        expectedFacet,
        projectName, "Kotlin"
      )
    }

  }

  override fun isIdeFrameRun(): Boolean =
    if (KotlinTestProperties.isArtifactFinalRelease) true
    else {
      logInfo("Maven archetype for the tested artifact ${KotlinTestProperties.kotlin_artifact_version} is absent."+
                 " This is not a bug, but the test '${testMethod.methodName}' is skipped (though marked as passed)")
      false
    }
}