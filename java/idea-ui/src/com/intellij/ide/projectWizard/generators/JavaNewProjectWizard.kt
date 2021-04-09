// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.wizard.*
import com.intellij.ide.wizard.NewProjectWizard.Companion.PLACE
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.ui.components.JBLabel
import java.awt.Dimension

class JavaNewProjectWizard : NewProjectWizard<JavaSettings> {
  override val language: String = "Java"
  override var settingsFactory = { JavaSettings() }

  override fun settingsList(settings: JavaSettings): List<LabelAndComponent> {
    val buildSystemButtons = BuildSystemType.EP_BUILD_SYSTEM.extensions
      .filter { it.name == GradleGroovy.name || it.name == Maven.name || it.name == Intellij.name }
      .map { BuildSystemButton(it, settings::buildSystemSettings) }

    buildSystemButtons.first().setSelected(true)

    return listOf(
      LabelAndComponent(JBLabel(JavaUiBundle.message("label.project.wizard.new.project.build.system")),
                        ActionManager.getInstance().createActionToolbar(PLACE, DefaultActionGroup(buildSystemButtons.toList()), true)
                          .component),
      LabelAndComponent(JBLabel(JavaUiBundle.message("label.project.wizard.new.project.jdk")),
                        JdkComboBox(null, ProjectSdksModel(), null, null, null, null)
                          .apply { minimumSize = Dimension(0, 0) })
    )
  }

  override fun setupProject(project: Project?, settings: JavaSettings) {
    settings
  }
}

class JavaSettings {
  var version: String = "1.0"
  var buildSystemSettings: String = "Gradle Groovy"
}