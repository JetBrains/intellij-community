// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.ide.plugins.UIComponentVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import javax.swing.Icon
import javax.swing.JComponent

interface UIComponentEditorTab : Disposable {

  val name: String

  val icon: Icon? get() = null

  val component: JComponent

  val preferredFocusedComponent: JComponent? get() = null

  override fun dispose() {

  }

  companion object {
    fun show(project: Project, tab: UIComponentEditorTab) {
      val file = UIComponentVirtualFile(tab.name, object : UIComponentVirtualFile.Content {
        override fun getIcon() = tab.icon
        override fun createComponent() = tab.component
        override fun getPreferredFocusedComponent() = tab.preferredFocusedComponent
      })
      val editorManager = FileEditorManager.getInstance(project)
      val editors = editorManager.openFile(file, true)
      for (editor in editors) {
        Disposer.register(editor, tab)
      }
    }
  }
}