// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JButton
import javax.swing.JPanel

internal class ProjectJdkConfigurableUi {
  lateinit var jdkComboBox: JdkComboBox
  lateinit var editButton: JButton

  fun panel(project: Project, jdkModel: ProjectSdksModel): JPanel = panel {
    row {
      val component = if (Registry.`is`("java.home.finder.use.eel"))
        JdkComboBox.createCombobox(project,
                                   jdkModel,
                                   SimpleJavaSdkType.notSimpleJavaSdkType(),
                                   filterSdkByEel(project),
                                   filterSdkSuggestionByEel(project),
                                   null)
      else
        JdkComboBox.createCombobox(project,
                                   jdkModel,
                                   SimpleJavaSdkType.notSimpleJavaSdkType(),
                                   WslSdkFilter.filterSdkByWsl(project),
                                   WslSdkFilter.filterSdkSuggestionByWsl(project),
                                   null)
      jdkComboBox = cell(component)
        .resizableColumn()
        .align(AlignX.FILL)
        .component
      editButton = button(ApplicationBundle.message("button.edit")) {}.component
    }
  }
}