// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel
import org.junit.Test

class CreateGradleProjectAndConfigureOldKotlinGuiTest : KotlinGuiTestCase() {

  @Test
  @JvmName("gradle_cfg_jvm_old1231")
  fun createGradleAndConfigureKotlinJvmOldVersion1231() {
    KotlinTestProperties.kotlin_artifact_version = "1.2.31"
    testCreateGradleAndConfigureKotlin(
      kotlinKind = KotlinKind.JVM,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinLibs[KotlinKind.JVM]!!.gradleGProject,
      expectedFacet = FacetStructure(
        targetPlatform = TargetPlatform.JVM18,
        languageVersion = LanguageVersion.L12,
        apiVersion = LanguageVersion.L12,
        jvmOptions = FacetStructureJVM()
      ),
      gradleOptions = NewProjectDialogModel.GradleProjectOptions(
        artifact = testMethod.methodName
      )
    )
  }

  @Test
  @JvmName("gradle_cfg_jvm_old1161")
  fun createGradleAndConfigureKotlinJvmOldVersion1161() {
    KotlinTestProperties.kotlin_artifact_version = "1.1.61"
    testCreateGradleAndConfigureKotlin(
      kotlinKind = KotlinKind.JVM,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinLibs[KotlinKind.JVM]!!.gradleGProject,
      expectedFacet = FacetStructure(
        targetPlatform = TargetPlatform.JVM18,
        languageVersion = LanguageVersion.L11,
        apiVersion = LanguageVersion.L11,
        jvmOptions = FacetStructureJVM()
      ),
      gradleOptions = NewProjectDialogModel.GradleProjectOptions(
        artifact = testMethod.methodName
      )
    )
  }
}