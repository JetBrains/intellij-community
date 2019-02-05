// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogScenarios
import org.junit.Ignore
import org.junit.Test

class CreateJavaProjectAndConfigureKotlinGuiTest : KotlinGuiTestCase() {
  private val testedJdk = "1.8"

  @Test
  @JvmName("kotlin_cfg_jvm_with_lib")
  fun configureKotlinJvmWithLibInJavaProject() {
    createJavaProject(projectFolder, testedJdk)
    configureKotlinJvm(libInPlugin = false)
    checkKotlinLibInProject(
      projectPath = projectFolder,
      expectedLibs = kotlinProjects.getValue(Projects.JavaProject).jars.getJars(KotlinTestProperties.kotlin_artifact_version)
)
    projectStructureDialogScenarios.checkKotlinLibsInStructureFromProject(
      projectPath = projectFolder,
      expectedLibName = kotlinProjects.getValue(Projects.JavaProject).libName!!
    )
  }

  @Test
  @JvmName("kotlin_cfg_jvm_no_lib")
  fun configureKotlinJvmInJavaProject() {
    createJavaProject(projectFolder, testedJdk)
    configureKotlinJvm(libInPlugin = true)
    projectStructureDialogScenarios.checkKotlinLibsInStructureFromPlugin(
      project = kotlinProjects.getValue(Projects.JavaProject),
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version)
  }

  @Test
  @JvmName("kotlin_cfg_js_with_lib")
  fun configureKotlinJSWithLibInJavaProject() {
    createJavaProject(projectFolder, testedJdk)
    configureKotlinJs(libInPlugin = false)
    checkKotlinLibInProject(
      projectPath = projectFolder,
      expectedLibs = kotlinProjects.getValue(Projects.KotlinProjectJs).jars.getJars(KotlinTestProperties.kotlin_artifact_version)
    )
    projectStructureDialogScenarios.checkKotlinLibsInStructureFromProject(
      projectPath = projectFolder,
      expectedLibName = kotlinProjects.getValue(Projects.KotlinProjectJs).libName!!
    )
  }

  @Test
  @JvmName("kotlin_cfg_js_no_lib")
  fun configureKotlinJSInJavaProject() {
    createJavaProject(projectFolder, testedJdk)
    configureKotlinJs(libInPlugin = true)
    projectStructureDialogScenarios.checkKotlinLibsInStructureFromPlugin(
      project = kotlinProjects.getValue(Projects.KotlinProjectJs),
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version)
  }

}