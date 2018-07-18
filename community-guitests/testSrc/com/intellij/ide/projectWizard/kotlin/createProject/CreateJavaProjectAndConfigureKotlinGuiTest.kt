// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import org.junit.Ignore
import org.junit.Test

class CreateJavaProjectAndConfigureKotlinGuiTest : KotlinGuiTestCase() {
  @Test
  @JvmName("kotlin_cfg_jvm_with_lib")
  fun configureKotlinJvmWithLibInJavaProject() {
    createJavaProject(projectFolder)
    configureKotlinJvm(libInPlugin = false)
    checkKotlinLibInProject(
      projectPath = projectFolder,
      kotlinKind = KotlinKind.JVM,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version)
    checkKotlinLibsInStructureFromProject(
      projectPath = projectFolder,
      kotlinKind = KotlinKind.JVM,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version)
  }

  @Test
  @JvmName("kotlin_cfg_jvm_no_lib")
  fun configureKotlinJvmInJavaProject() {
    createJavaProject(projectFolder)
    configureKotlinJvm(libInPlugin = true)
    checkKotlinLibsInStructureFromPlugin(
      kotlinKind = KotlinKind.JVM,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version)
  }

  @Test
  @JvmName("kotlin_cfg_js_with_lib")
  fun configureKotlinJSWithLibInJavaProject() {
    createJavaProject(projectFolder)
    configureKotlinJs(libInPlugin = false)
    checkKotlinLibInProject(
      projectPath = projectFolder,
      kotlinKind = KotlinKind.JS,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version)
    checkKotlinLibsInStructureFromProject(
      projectPath = projectFolder,
      kotlinKind = KotlinKind.JS,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version)
  }

  @Test
  @JvmName("kotlin_cfg_js_no_lib")
  fun configureKotlinJSInJavaProject() {
    createJavaProject(projectFolder)
    configureKotlinJs(libInPlugin = true)
    checkKotlinLibsInStructureFromPlugin(
      kotlinKind = KotlinKind.JS,
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version)
  }

  @Test
  @Ignore
  @JvmName("kotlin_cfg_js_no_lib_from_file")
  fun configureKotlinJSInJavaProjectFromKotlinFile() {
    createJavaProject(projectFolder)
//    createKotlinFile(
//        projectName = projectFolder.name,
//        fileName = "K1")
//    ideFrame {   popupClick("org.jetbrains.kotlin.idea.configuration.KotlinGradleModuleConfigurator@4aae5ef8")
//    }
//    configureKotlinJs(libInPlugin = true)
//      checkKotlinLibsInStructureFromPlugin(
//          projectType = KotlinKind.JS,
//          errors = collector)
  }


}