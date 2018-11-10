// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.util.*
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel
import org.junit.Test

class CreateGradleKotlinDslProjectAndConfigureKotlinGuiTest : KotlinGuiTestCase() {

  @Test
  @JvmName("gradle_k_cfg_jvm")
  fun createGradleAndConfigureKotlinJvmActualVersion() {
    testCreateGradleAndConfigureKotlin(
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinProjects.getValue(Projects.GradleKProjectJvm),
      expectedFacet = defaultFacetSettings[TargetPlatform.JVM18]!!,
      gradleOptions = NewProjectDialogModel.GradleProjectOptions(
        artifact = testMethod.methodName,
        useKotlinDsl = kotlinProjects.getValue(Projects.GradleKProjectJvm).isKotlinDsl
      )
    )
  }

  @Test
  @JvmName("gradle_k_cfg_js")
  fun createGradleAndConfigureKotlinJsActualVersion() {
    testCreateGradleAndConfigureKotlin(
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinProjects.getValue(Projects.GradleKProjectJs),
      expectedFacet = defaultFacetSettings[TargetPlatform.JavaScript]!!,
      gradleOptions = NewProjectDialogModel.GradleProjectOptions(
        artifact = testMethod.methodName,
        useKotlinDsl = kotlinProjects.getValue(Projects.GradleKProjectJs).isKotlinDsl
      )
    )
  }

  override fun isIdeFrameRun(): Boolean =
    if (KotlinTestProperties.isActualKotlinUsed() && !KotlinTestProperties.isArtifactPresentInConfigureDialog) {
      logInfo(
        "The tested artifact ${KotlinTestProperties.kotlin_artifact_version} is not present in the configuration dialog. This is not a bug, but the test '${testMethod.methodName}' is skipped (though marked as passed)")
      false
    }
    else true
}