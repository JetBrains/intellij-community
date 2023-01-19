package com.intellij.lang.documentation.ide.impl

import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.ide.actions.DOCUMENTATION_TARGETS
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

@Service
class DocumentationManagementHelper(private val project: Project) {
  fun showQuickDoc(editor: Editor, target: DocumentationTarget, popupClosedCallback: (() -> Unit)? = null) {
    val context = DataContext {
      when (it) {
        CommonDataKeys.EDITOR.name -> editor
        DOCUMENTATION_TARGETS.name -> listOf(target)
        else -> null
      }
    }
    DocumentationManager.instance(project).actionPerformed(context, popupClosedCallback)
  }

  companion object {
    @JvmStatic
    fun instance(project: Project): DocumentationManagementHelper = project.service()
  }
}