// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogScenarios
import org.junit.Test

class CreateKotlinProjectGuiTest : KotlinGuiTestCase() {
  @Test
  @JvmName("pure_kotlin_jvm")
  fun createKotlinJvmProject() {
    createKotlinProject(projectFolder, kotlinProjects.getValue(Projects.KotlinProjectJvm))
    projectStructureDialogScenarios.checkKotlinLibsInStructureFromPlugin(
      project = kotlinProjects.getValue(Projects.KotlinProjectJvm),
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version)
  }

  @Test
  @JvmName("pure_kotlin_js")
  fun createKotlinJsProject() {
    createKotlinProject(projectFolder, kotlinProjects.getValue(Projects.KotlinProjectJs))
    projectStructureDialogScenarios.checkKotlinLibsInStructureFromPlugin(
      project = kotlinProjects.getValue(Projects.KotlinProjectJs),
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version)
  }

}