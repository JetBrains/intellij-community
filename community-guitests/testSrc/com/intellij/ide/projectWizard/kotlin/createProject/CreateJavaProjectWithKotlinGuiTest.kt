// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.createProject

import com.intellij.ide.projectWizard.kotlin.model.KotlinKind
import com.intellij.ide.projectWizard.kotlin.model.kotlinLibs
import com.intellij.ide.projectWizard.kotlin.model.KotlinGuiTestCase
import com.intellij.ide.projectWizard.kotlin.model.checkKotlinLibsInStructureFromPlugin
import com.intellij.ide.projectWizard.kotlin.model.createJavaProject
import org.junit.Test

class CreateJavaProjectWithKotlinGuiTest : KotlinGuiTestCase() {
  @Test
  @JvmName("java_with_jvm")
  fun createJavaWithKotlinJvmProject() {
    createJavaProject(projectFolder, kotlinLibs[KotlinKind.JVM]!!.javaProject.frameworkName)
    ideFrame {
      checkKotlinLibsInStructureFromPlugin(
        kotlinKind = KotlinKind.JVM)
    }
  }

  @Test
  @JvmName("java_with_js")
  fun createJavaWithKotlinJSProject() {
    createJavaProject(projectFolder, kotlinLibs[KotlinKind.JS]!!.javaProject.frameworkName)
    ideFrame {
      checkKotlinLibsInStructureFromPlugin(
        kotlinKind = KotlinKind.JS)
    }
  }

}
