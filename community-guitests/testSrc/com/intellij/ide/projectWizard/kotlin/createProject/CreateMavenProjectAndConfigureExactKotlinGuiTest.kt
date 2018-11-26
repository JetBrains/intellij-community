// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.framework.param.GuiTestSuiteParam
import com.intellij.testGuiFramework.util.logInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.Serializable

@RunWith(GuiTestSuiteParam::class)
class CreateMavenProjectAndConfigureExactKotlinGuiTest(private val testParameters: TestParameters) : KotlinGuiTestCase() {

  data class TestParameters(
    val projectName: String,
    val kotlinVersion: String,
    val project: ProjectProperties,
    val expectedFacet: FacetStructure) : Serializable {
    override fun toString() = projectName
  }

  @Test
  fun testCreateMavenAndConfigureKotlin() {
    KotlinTestProperties.kotlin_artifact_version = testParameters.kotlinVersion
    createMavenAndConfigureKotlin(
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = testParameters.project,
      expectedFacet = testParameters.expectedFacet
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<TestParameters> {
      return listOf(
        TestParameters(
          projectName = "maven_cfg_jvm_130",
          project = kotlinProjects.getValue(Projects.MavenProjectJvm),
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JVM18),
          kotlinVersion = "1.3.0"
        ),
        TestParameters(
          projectName = "maven_cfg_js_130",
          project = kotlinProjects.getValue(Projects.MavenProjectJs),
          expectedFacet = defaultFacetSettings.getValue(TargetPlatform.JavaScript),
          kotlinVersion = "1.3.0"
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