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
class CreateMavenProjectAndConfigureKotlinGuiTest(private val testParameters: TestParameters) : KotlinGuiTestCase() {

  enum class KotlinKind{Jvm, Js}

  data class TestParameters(
    val projectName: String,
    val project: ProjectProperties,
    val kotlinKind: KotlinKind,
    val expectedFacet: FacetStructure) : Serializable {
    override fun toString() = projectName
  }

  @Test
  fun createMavenAndConfigureKotlin() {
    val projectName = testMethod.methodName
    val kotlinVersion = KotlinTestProperties.kotlin_artifact_version
    if (!isIdeFrameRun()) return
    createMavenProject(
      projectPath = projectFolder,
      artifact = projectName)
    waitAMoment()
    when(testParameters.kotlinKind){
      KotlinKind.Jvm -> configureKotlinJvmFromMaven(kotlinVersion)
      KotlinKind.Js -> configureKotlinJsFromMaven(kotlinVersion)
    }

    waitAMoment()
    saveAndCloseCurrentEditor()
    editPomXml(
      kotlinVersion = kotlinVersion
    )
    mavenReimport()
    Pause.pause(5000)
    mavenReimport()

    projectStructureDialogScenarios.openProjectStructureAndCheck {
      projectStructureDialogModel.checkLibrariesFromMavenGradle(
        buildSystem = BuildSystem.Maven,
        kotlinVersion = kotlinVersion,
        expectedJars = testParameters.project.jars.getJars(kotlinVersion)
      )
      projectStructureDialogModel.checkFacetInOneModule(
        expectedFacet = testParameters.expectedFacet,
        path = *arrayOf(projectName, "Kotlin")
      )
    }
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<TestParameters> {
      return listOf(
        TestParameters(
          projectName = "maven_cfg_jvm",
          project = kotlinProjects.getValue(Projects.MavenProjectJvm),
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JVM18),
          kotlinKind = KotlinKind.Jvm
        ),
        TestParameters(
          projectName = "maven_cfg_js",
          project = kotlinProjects.getValue(Projects.MavenProjectJs),
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JavaScript),
          kotlinKind = KotlinKind.Js
        )
      )
    }
  }

  override fun isIdeFrameRun(): Boolean =
      if (KotlinTestProperties.isActualKotlinUsed() && !KotlinTestProperties.isArtifactPresentInConfigureDialog) {
        logInfo("The tested artifact ${KotlinTestProperties.kotlin_artifact_version} is not present in the configuration dialog. This is not a bug, but the test '${testMethod.methodName}' is skipped (though marked as passed)")
        false
      }
      else true

}