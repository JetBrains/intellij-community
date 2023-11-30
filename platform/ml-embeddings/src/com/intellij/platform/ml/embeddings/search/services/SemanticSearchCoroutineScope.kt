package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class SemanticSearchCoroutineScope(private val cs: CoroutineScope) {
  companion object {
    fun getScope(project: Project) = project.service<SemanticSearchCoroutineScope>().cs
  }
}