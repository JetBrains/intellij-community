// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.framework.param.GuiTestSuiteParam
import com.intellij.testGuiFramework.impl.mavenReimport
import com.intellij.testGuiFramework.impl.waitAMoment
import com.intellij.testGuiFramework.util.*
import com.intellij.testGuiFramework.util.scenarios.openProjectStructureAndCheck
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogModel
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogScenarios
import org.fest.swing.timing.Pause
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.Serializable

@RunWith(GuiTestSuiteParam::class)
class CreateMavenProjectWithKotlinGuiTest(private val testParameters: TestParameters) : KotlinGuiTestCase() {

  enum class KotlinKind{Jvm, Js}

  data class TestParameters(
    val projectName: String,
    val archetype: String,
    val kotlinKind: KotlinKind,
    val expectedFacet: FacetStructure) : Serializable {
    override fun toString() = projectName
  }

  @Test
  fun createMavenWithKotlin() {
    val projectName = testMethod.methodName
    val kotlinVersion = KotlinTestProperties.kotlin_artifact_version
    if (!isIdeFrameRun()) return
    createMavenProject(
      projectPath = projectFolder,
      artifact = projectName,
      archetype = testParameters.archetype,
      kotlinVersion = kotlinVersion,
      projectSdk = "1.8")
    waitAMoment()
    mavenReimport()
    // TODO: remove extra mavenReimport after GUI-72 fixing
    Pause.pause(5000)
    waitAMoment()
    mavenReimport()
    waitAMoment()

    val expectedFacet = when(testParameters.kotlinKind) {
      KotlinKind.Js -> {
        val expectedOutput = "$projectFolder/target/classes/${projectName}.js".replace("\\", "/")
        defaultFacetSettings[TargetPlatform.JavaScript]!!.copy(
          cmdParameters = "-output $expectedOutput",
          jsOptions = testParameters.expectedFacet.jsOptions?.copy(
            generateSourceMap = true
          )
        )
      }
      KotlinKind.Jvm -> testParameters.expectedFacet
    }

    val expectedJars = when(testParameters.kotlinKind) {
      KotlinKind.Js -> kotlinProjects.getValue(Projects.MavenProjectJs).jars.getJars(kotlinVersion)
      KotlinKind.Jvm ->  // TODO: use default set after fix KT-21230
        listOf(
          "org.jetbrains.kotlin:kotlin-stdlib:",
          "org.jetbrains.kotlin:kotlin-test:",
          "org.jetbrains:annotations:13.0"
        )
    }

    projectStructureDialogScenarios.openProjectStructureAndCheck {
      projectStructureDialogModel.checkLibrariesFromMavenGradle(
        buildSystem = BuildSystem.Maven,
        kotlinVersion = kotlinVersion,
        expectedJars = expectedJars
      )
      projectStructureDialogModel.checkFacetInOneModule(
        expectedFacet = expectedFacet,
        path = *arrayOf(projectName, "Kotlin")
      )
    }

    waitAMoment()
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<TestParameters> {
      return listOf(
        TestParameters(
          projectName = "maven_with_jvm",
          archetype = kotlinProjects.getValue(Projects.MavenProjectJvm).frameworkName,
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JVM16),
          kotlinKind = KotlinKind.Jvm
        ),
        TestParameters(
          projectName = "maven_with_js",
          archetype = kotlinProjects.getValue(Projects.MavenProjectJs).frameworkName,
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JavaScript),
          kotlinKind = KotlinKind.Js
        )
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