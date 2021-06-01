// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.LabelAndComponent
import com.intellij.ide.wizard.BuildSystemType
import com.intellij.openapi.extensions.ExtensionPointName
import javax.swing.JLabel

abstract class JavaBuildSystemType(override val name: String) : BuildSystemType<JavaSettings>(name) {
  companion object{
    var EP_NAME = ExtensionPointName<JavaBuildSystemType>("com.intellij.newProjectWizard.buildSystem.java")
  }
}

class GradleJavaBuildSystemType : JavaBuildSystemType("Gradle") {
  override fun setupProject(settings: JavaSettings) {
    TODO("Not yet implemented")
  }

  override val advancedSettings: List<LabelAndComponent> = listOf(LabelAndComponent(component = JLabel("Gradle Advanced Settings")))
}

class MavenJavaBuildSystemType : JavaBuildSystemType("Maven") {
  override fun setupProject(settings: JavaSettings) {
    TODO("Not yet implemented")
  }
}

class IntelliJJavaBuildSystemType : JavaBuildSystemType("IntelliJ") {
  override fun setupProject(settings: JavaSettings) {
    TODO("Not yet implemented")
  }
}