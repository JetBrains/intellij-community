package com.intellij.platform.lsp.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class LspCoroutineScopeService(val cs: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): LspCoroutineScopeService = project.service()
  }
}