// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.util.logInfo
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel
import org.junit.Test

class CreateGradleProjectAndConfigureKotlinGuiTest : KotlinGuiTestCase() {

  @Test
  @JvmName("gradle_cfg_jvm")
  fun createGradleAndConfigureKotlinJvmActualVersion() {
    testCreateGradleAndConfigureKotlin(
      kotlinKind = KotlinKind.JVM,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinLibs[KotlinKind.JVM]!!.gradleGProject,
      expectedFacet = defaultFacetSettings[TargetPlatform.JVM18]!!,
      gradleOptions = NewProjectDialogModel.GradleProjectOptions(
        artifact = testMethod.methodName
      )
    )
  }

  @Test
  @JvmName("gradle_cfg_js")
  fun createGradleAndConfigureKotlinJsActualVersion() {
    testCreateGradleAndConfigureKotlin(
      kotlinKind = KotlinKind.JS,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinLibs[KotlinKind.JS]!!.gradleGProject,
      expectedFacet = defaultFacetSettings[TargetPlatform.JavaScript]!!,
      gradleOptions = NewProjectDialogModel.GradleProjectOptions(
        artifact = testMethod.methodName
      )
    )
  }

  override fun isIdeFrameRun(): Boolean =
      if (KotlinTestProperties.isActualKotlinUsed() && !KotlinTestProperties.isArtifactPresentInConfigureDialog) {
        logInfo("The tested artifact ${KotlinTestProperties.kotlin_artifact_version} is not present in the configuration dialog. This is not a bug, but the test '${testMethod.methodName}' is skipped (though marked as passed)")
        false
      }
      else true
}