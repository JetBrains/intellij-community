// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.*
import org.junit.Test

class CreateJavaProjectWithKotlinGuiTest : KotlinGuiTestCase() {
  @Test
  @JvmName("java_with_jvm")
  fun createJavaWithKotlinJvmProject() {
    createJavaProject(projectFolder, kotlinLibs[KotlinKind.JVM]!!.javaProject.frameworkName)
    ideFrame {
      checkKotlinLibsInStructureFromPlugin(
        kotlinKind = KotlinKind.JVM,
        kotlinVersion = KotlinTestProperties.kotlin_artifact_version
      )
    }
  }
}
