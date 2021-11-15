// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.UIComponentVirtualFile
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import javax.swing.JComponent

abstract class AbstractAnalyzeDependenciesAction : AnAction(), DumbAware {
  abstract fun getSystemId(e: AnActionEvent): ProjectSystemId?

  abstract fun getExternalProjectPath(e: AnActionEvent): String?

  abstract fun getDependency(e: AnActionEvent): DependencyContributor.Dependency?

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val systemId = getSystemId(e) ?: return
    val externalProjectPath = getExternalProjectPath(e)
    val dependency = getDependency(e)
    val contributor = DependencyAnalyzerExtension.findContributor(project, systemId) ?: return
    val disposable = Disposer.newDisposable()
    val dependencyAnalyzerView = DependencyAnalyzerViewImpl(contributor, disposable)
    if (externalProjectPath != null) {
      if (dependency != null) {
        dependencyAnalyzerView.setSelectedDependency(externalProjectPath, dependency)
      }
      else {
        dependencyAnalyzerView.setSelectedExternalProject(externalProjectPath)
      }
    }
    val editorTabName = ExternalSystemBundle.message("external.system.dependency.analyzer.editor.tab.name")
    val file = UIComponentVirtualFile(editorTabName, object : UIComponentVirtualFile.Content {
      override fun getIcon() = AllIcons.Actions.DependencyAnalyzer
      override fun createComponent() = dependencyAnalyzerView.component
      override fun getPreferredFocusedComponent(): JComponent? = null
    })
    val editorManager = FileEditorManager.getInstance(project)
    val editors = editorManager.openFile(file, true)
    for (editor in editors) {
      Disposer.register(editor, disposable)
    }
  }

  init {
    templatePresentation.icon = AllIcons.Actions.DependencyAnalyzer
    templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.action.name")
    templatePresentation.isEnabledAndVisible = Registry.`is`("external.system.dependency.analyzer")
  }
}