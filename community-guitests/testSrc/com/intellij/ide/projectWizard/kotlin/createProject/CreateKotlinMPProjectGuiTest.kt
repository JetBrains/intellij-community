// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.launcher.system.SystemInfo
import com.intellij.testGuiFramework.framework.param.GuiTestSuiteParam
import com.intellij.testGuiFramework.impl.gradleReimport
import com.intellij.testGuiFramework.impl.waitAMoment
import com.intellij.testGuiFramework.impl.waitForGradleReimport
import com.intellij.testGuiFramework.util.logInfo
import com.intellij.testGuiFramework.util.scenarios.openProjectStructureAndCheck
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogModel
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogScenarios
import com.intellij.testGuiFramework.util.step
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.Serializable

@RunWith(GuiTestSuiteParam::class)
class CreateKotlinMPProjectGuiTest(private val testParameters: TestParameters) : KotlinGuiTestCase() {

  data class TestParameters(
    val projectName: String,
    val project: ProjectProperties,
    val gradleGroup: String,
    val suffixes: Map<TargetPlatform, String>,
    val facets: Map<TargetPlatform, FacetStructure>) : Serializable {
    override fun toString() = projectName
  }

  /**
   * Kotlin Multiplatform project templates available in Kotlin version started from 1.3 rc
   * */
  override fun isIdeFrameRun(): Boolean {
    return if (versionFromPlugin.toString() >= "1.3") true
    else {
      logInfo("Project '${testParameters.project.frameworkName}' is not available in the Kotlin version $versionFromPlugin")
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
      templateName = testParameters.project.frameworkName,
      projectSdk = "1.8"
    )

    waitAMoment()
    step("wait for initial gradle import") {
      waitForGradleReimport(projectName)
    }
    editSettingsGradle()
    editBuildGradle(
      kotlinVersion = kotlinVersion,
      isKotlinDslUsed = false
    )

    step("gradle reimport after editing gradle files") {
      gradleReimport()
      assert(waitForGradleReimport(projectName)) { "Gradle import failed after editing of gradle files" }
    }
    waitAMoment()

    projectStructureDialogScenarios.openProjectStructureAndCheck {
      projectStructureDialogModel.checkLibrariesFromMavenGradle(
        BuildSystem.Gradle,
        kotlinVersion,
        testParameters.project.jars.getJars(kotlinVersion)
      )

      testParameters.project.modules.forEach { platform: TargetPlatform ->
        listOf("Main", "Test").forEach { moduleKind: String ->
          val path = arrayOf(projectName, "${testParameters.suffixes[platform]!!}$moduleKind", "Kotlin")
          projectStructureDialogModel.checkFacetInOneModule(testParameters.facets[platform]!!, path = *path)
        }
      }
    }
    waitAMoment()
  }

  companion object {
    private val nativeSuffix = when(SystemInfo.getSystemType()){
      SystemInfo.SystemType.WINDOWS -> "mingw"
      SystemInfo.SystemType.UNIX -> "linux"
      SystemInfo.SystemType.MAC -> "macos"
    }

    private val suffixes = mapOf(
      TargetPlatform.JVM16 to "jvm",
      TargetPlatform.JVM18 to "jvm",
      TargetPlatform.JavaScript to "js",
      TargetPlatform.Common to "common",
      TargetPlatform.Native to nativeSuffix
    )

    private val mobileSuffixes = mapOf(
      TargetPlatform.JVM16 to "jvm",
      TargetPlatform.JVM18 to "jvm",
      TargetPlatform.JavaScript to "js",
      TargetPlatform.Common to "common",
      TargetPlatform.Native to "ios"
    )

    private val jsOptions = defaultFacetSettings[TargetPlatform.JavaScript]!!.jsOptions!!.copy(
      generateSourceMap = true,
      moduleKind = FacetJSModuleKind.UMD
      )

    private val defaultFacets = TargetPlatform.values().map { Pair(it, defaultFacetSettings[it]!!) }.toMap()
    private val clientServerFacets = TargetPlatform.values().map {
      if(it == TargetPlatform.JavaScript)
        Pair(it, defaultFacets[it]!!.copy(jsOptions = jsOptions))
      else
      Pair(it, defaultFacetSettings[it]!!)
    }.toMap()

    private const val libraryGroup = "com.example"

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<TestParameters> {
      return listOf(
        TestParameters(
          projectName = "kotlin_mpp_library",
          project = kotlinProjects.getValue(Projects.KotlinMPProjectLibrary),
          suffixes = suffixes,
          gradleGroup = libraryGroup,
          facets = defaultFacets
        ),
        TestParameters(
          projectName = "kotlin_mpp_client_server",
          project = kotlinProjects.getValue(Projects.KotlinMPProjectClientServer),
          suffixes = suffixes,
          gradleGroup = "",
          facets = clientServerFacets
        ),
        TestParameters(
          projectName = "kotlin_mpp_native",
          project = kotlinProjects.getValue(Projects.KotlinProjectNative),
          suffixes = suffixes,
          gradleGroup = "",
          facets = defaultFacets
        ),
        TestParameters(
          projectName = "kotlin_mpp_mobile_library",
          project = kotlinProjects.getValue(Projects.KotlinMPProjectMobileLibrary),
          suffixes = mobileSuffixes,
          gradleGroup = libraryGroup,
          facets = defaultFacets
        )
      )
    }
  }

}