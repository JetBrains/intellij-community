// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.framework.param.GuiTestSuiteParam
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.Serializable

@RunWith(GuiTestSuiteParam::class)
class CreateGradleProjectAndConfigureExactKotlinGuiTest(private val testParameters: TestParameters) : KotlinGuiTestCase() {

  data class TestParameters(
    val projectName: String,
    val kotlinVersion: String,
    val project: ProjectProperties = kotlinProjects.getValue(Projects.GradleGProjectJvm),
    val expectedFacet: FacetStructure) : Serializable {
    override fun toString() = projectName
  }

  @Test
  fun createGradleAndConfigureKotlinJvmExactVersion() {
    KotlinTestProperties.kotlin_artifact_version = testParameters.kotlinVersion
    testCreateGradleAndConfigureKotlin(
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = testParameters.project,
      expectedFacet = testParameters.expectedFacet,
      gradleOptions = NewProjectDialogModel.GradleProjectOptions(
        artifact = testMethod.methodName
      )
    )
  }

  companion object {
    private val expectedFacet11 = FacetStructure(
      targetPlatform = TargetPlatform.JVM18,
      languageVersion = LanguageVersion.L11,
      apiVersion = LanguageVersion.L11,
      jvmOptions = FacetStructureJVM()
    )

    private val expectedFacet12 =
      expectedFacet11.copy(apiVersion = LanguageVersion.L12, languageVersion = LanguageVersion.L12)
    private val expectedFacet13 =
      expectedFacet11.copy(apiVersion = LanguageVersion.L13, languageVersion = LanguageVersion.L13)

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<TestParameters> {
      return listOf(
        TestParameters(
          projectName = "gradle_cfg_jvm_1271",
          expectedFacet = expectedFacet12,
          kotlinVersion = "1.2.71"
        ),
        TestParameters(
          projectName = "gradle_cfg_jvm_130",
          expectedFacet = expectedFacet13,
          kotlinVersion = "1.3.0"
        )
      )
    }
  }
}