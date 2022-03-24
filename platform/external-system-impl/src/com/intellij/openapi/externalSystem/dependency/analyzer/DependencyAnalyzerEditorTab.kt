// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.SplitAction
import com.intellij.ide.plugins.UIComponentVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DependencyAnalyzerEditorTab(private val project: Project, systemId: ProjectSystemId) : Disposable {

  val view = DependencyAnalyzerViewImpl(project, systemId, this)

  private val file: VirtualFile by lazy {
    val name = ExternalSystemBundle.message("external.system.dependency.analyzer.editor.tab.name")
    UIComponentVirtualFile(name, object : UIComponentVirtualFile.Content {
      override fun getIcon() = AllIcons.Actions.DependencyAnalyzer
      override fun createComponent() = view.createComponent()
      override fun getPreferredFocusedComponent() = null
    }).apply {
      putUserData(SplitAction.FORBID_TAB_SPLIT, true)
    }
  }

  fun show() {
    val editorManager = FileEditorManager.getInstance(project)
    val editors = editorManager.openFile(file, true)
    for (editor in editors) {
      Disposer.register(editor, this)
    }
  }

  override fun dispose() {}
}