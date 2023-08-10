// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.ui.dsl.builder.*
import javax.swing.JButton
import javax.swing.JPanel

class ProjectJdkConfigurableUi {

  lateinit var jdkComboBox: JdkComboBox
  lateinit var editButton: JButton

  fun panel(project: Project, jdkModel: ProjectSdksModel): JPanel = panel {
    row {
      jdkComboBox = cell(
        JdkComboBox(project, jdkModel, SimpleJavaSdkType.notSimpleJavaSdkType(),
          WslSdkFilter.filterSdkByWsl(project), WslSdkFilter.filterSdkSuggestionByWsl(project),
          null, null)).component
      editButton = button(ApplicationBundle.message("button.edit")) {}.component
    }
  }

}