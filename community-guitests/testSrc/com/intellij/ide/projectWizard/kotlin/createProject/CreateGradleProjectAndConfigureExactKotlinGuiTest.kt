// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel
import org.junit.Test

class CreateGradleProjectAndConfigureExactKotlinGuiTest : KotlinGuiTestCase() {

  @Test
  @JvmName("gradle_cfg_jvm_old1271")
  fun createGradleAndConfigureKotlinJvmExactVersion1271() {
    KotlinTestProperties.kotlin_artifact_version = "1.2.71"
    testCreateGradleAndConfigureKotlin(
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinProjects.getValue(Projects.GradleGProjectJvm),
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

  fun createGradleAndConfigureKotlinJvmExactVersion130() {
    KotlinTestProperties.kotlin_artifact_version = "1.3.0"
    testCreateGradleAndConfigureKotlin(
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinProjects.getValue(Projects.GradleGProjectJvm),
      expectedFacet = FacetStructure(
        targetPlatform = TargetPlatform.JVM18,
        languageVersion = LanguageVersion.L13,
        apiVersion = LanguageVersion.L13,
        jvmOptions = FacetStructureJVM()
      ),
      gradleOptions = NewProjectDialogModel.GradleProjectOptions(
        artifact = testMethod.methodName
      )
    )
  }

  @Test
  @JvmName("gradle_cfg_jvm_old1161")
  fun createGradleAndConfigureKotlinJvmExactVersion1161() {
    KotlinTestProperties.kotlin_artifact_version = "1.1.61"
    testCreateGradleAndConfigureKotlin(
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version,
      project = kotlinProjects.getValue(Projects.GradleGProjectJvm),
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