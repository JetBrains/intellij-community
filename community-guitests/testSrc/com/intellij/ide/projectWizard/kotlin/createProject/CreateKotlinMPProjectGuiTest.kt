// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.framework.param.GuiTestSuiteParam
import com.intellij.testGuiFramework.impl.gradleReimport
import com.intellij.testGuiFramework.impl.waitAMoment
import com.intellij.testGuiFramework.impl.waitForGradleReimport
import com.intellij.testGuiFramework.util.logInfo
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel
import com.intellij.testGuiFramework.util.scenarios.openProjectStructureAndCheck
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogModel
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogScenarios
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.Serializable

@RunWith(GuiTestSuiteParam::class)
class CreateKotlinMPProjectGuiTest(private val testParameters: TestParameters) : KotlinGuiTestCase() {


  data class TestParameters(
    val projectName: String,
    val templateName: String,
    val modules: Set<TargetPlatform>) : Serializable {
    override fun toString() = projectName
  }

  /**
   * Kotlin Multiplatform project templates available in Kotlin version started from 1.3 rc
   * */
  override fun isIdeFrameRun(): Boolean {
    return if (versionFromPlugin.toString() >= "1.3") true
    else {
      logInfo("Project '${testParameters.templateName}' is not available in the Kotlin version $versionFromPlugin")
      false
    }
  }

  @Test
  fun createKotlinMppProject() {
    val projectName = testMethod.methodName
    val kotlinVersion = KotlinTestProperties.kotlin_artifact_version
    if (!isIdeFrameRun()) return
    createKotlinMPProject(
      projectPath = projectFolder,
      templateName = testParameters.templateName
    )

    waitAMoment()
    waitForGradleReimport(projectName, waitForProject = false)
    editSettingsGradle()
    editBuildGradle(
      kotlinVersion = kotlinVersion,
      isKotlinDslUsed = false
    )

    gradleReimport()
    waitForGradleReimport(projectName, waitForProject = true)
    waitAMoment()

    val isNativeIncluded = testParameters.modules.contains(TargetPlatform.Native)
    val expectedJars = if (isNativeIncluded) {
      kotlinLibs[KotlinKind.Common]!!.kotlinMPProjectLibrary.jars.getJars(kotlinVersion) +
      kotlinLibs[KotlinKind.JVM]!!.kotlinMPProjectLibrary.jars.getJars(kotlinVersion) +
      kotlinLibs[KotlinKind.JS]!!.kotlinMPProjectLibrary.jars.getJars(kotlinVersion) +
      kotlinLibs[KotlinKind.Native]!!.kotlinMPProjectLibrary.jars.getJars(kotlinVersion)
    }
    else {
      kotlinLibs[KotlinKind.Common]!!.kotlinMPProjectClientServer.jars.getJars(kotlinVersion) +
      kotlinLibs[KotlinKind.JVM]!!.kotlinMPProjectClientServer.jars.getJars(kotlinVersion) +
      kotlinLibs[KotlinKind.JS]!!.kotlinMPProjectClientServer.jars.getJars(kotlinVersion)
    }
      .toSet()

    projectStructureDialogScenarios.openProjectStructureAndCheck {
      projectStructureDialogModel.checkLibrariesFromMavenGradle(
        BuildSystem.Gradle,
        kotlinVersion,
        expectedJars
      )

      testParameters.modules.forEach { platform: TargetPlatform ->
        listOf("Main", "Test").forEach { moduleKind: String ->
          val path = arrayOf(projectName, "${projectName}_${suffixes[platform]!!}$moduleKind", "Kotlin")
          projectStructureDialogModel.checkFacetInOneModule(defaultFacetSettings[platform]!!, path = *path)
        }
      }

    }
  }

  companion object {
    private val suffixes = mapOf(
      TargetPlatform.JVM16 to "jvm",
      TargetPlatform.JVM18 to "jvm",
      TargetPlatform.JavaScript to "js",
      TargetPlatform.Common to "common",
      TargetPlatform.Native to "ios"
    )

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<TestParameters> {
      return listOf(
        TestParameters(
          projectName = "kotlin_mpp_library",
          templateName = NewProjectDialogModel.Constants.itemKotlinMppLibrary,
          modules = setOf(TargetPlatform.JVM16, TargetPlatform.JavaScript, TargetPlatform.Common, TargetPlatform.Native)
        ),
        TestParameters(
          projectName = "kotlin_mpp_client_server",
          templateName = NewProjectDialogModel.Constants.itemKotlinMppClientServer,
          modules = setOf(TargetPlatform.JVM16, TargetPlatform.JavaScript, TargetPlatform.Common)
        )
      )
    }
  }

}