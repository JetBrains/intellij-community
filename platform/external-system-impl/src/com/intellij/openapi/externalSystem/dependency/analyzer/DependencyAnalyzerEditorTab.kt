// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import javax.swing.Icon
import javax.swing.JComponent

class DependencyAnalyzerEditorTab(project: Project, systemId: ProjectSystemId) : UIComponentEditorTab {

  val view = DependencyAnalyzerViewImpl(project, systemId, this)

  override val name: String = ExternalSystemBundle.message("external.system.dependency.analyzer.editor.tab.name")

  override val icon: Icon = AllIcons.Actions.DependencyAnalyzer

  override val component: JComponent = view.component
}