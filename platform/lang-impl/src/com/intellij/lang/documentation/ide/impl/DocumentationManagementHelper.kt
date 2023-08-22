// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.ide.documentation.DOCUMENTATION_TARGETS

@Service(Service.Level.PROJECT)
class DocumentationManagementHelper(private val project: Project) {
  fun showQuickDoc(editor: Editor, target: DocumentationTarget, popupClosedCallback: (() -> Unit)? = null) {
    val context = DataContext {
      when (it) {
        CommonDataKeys.EDITOR.name -> editor
        DOCUMENTATION_TARGETS.name -> listOf(target)
        else -> null
      }
    }
    DocumentationManager.getInstance(project).actionPerformed(context, popupClosedCallback)
  }

  companion object {
    fun getInstance(project: Project): DocumentationManagementHelper = project.service()
  }
}