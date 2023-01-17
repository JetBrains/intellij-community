package com.intellij.lang.documentation.ide.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service
class DocumentationPopupInfoProvider(private val project: Project) {
  val isPopupVisible: Boolean
    get() = DocumentationManager.instance(project).isPopupVisible

  companion object {
    @JvmStatic
    fun instance(project: Project): DocumentationPopupInfoProvider = project.service()
  }
}