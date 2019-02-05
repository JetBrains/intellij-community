// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import com.intellij.testGuiFramework.util.scenarios.NewProjectDialogModel
import com.intellij.testGuiFramework.util.scenarios.projectStructureDialogScenarios
import org.junit.Test

class CreateJavaProjectWithKotlinGuiTest : KotlinGuiTestCase() {
  private val testedJdk = "1.8"
  @Test
  @JvmName("java_with_jvm")
  fun createJavaWithKotlinJvmProject() {
    createJavaProject(projectFolder,
                      testedJdk,
                      setOf(NewProjectDialogModel.LibraryOrFramework(kotlinProjects.getValue(Projects.JavaProject).frameworkName)))
    projectStructureDialogScenarios.checkKotlinLibsInStructureFromPlugin(
      project = kotlinProjects.getValue(Projects.JavaProject),
      kotlinVersion = KotlinTestProperties.kotlin_artifact_version
    )
  }
}
