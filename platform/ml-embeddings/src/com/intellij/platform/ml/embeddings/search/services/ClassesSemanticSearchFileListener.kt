package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.search.settings.SemanticSearchSettings

@Service(Service.Level.PROJECT)
class ClassesSemanticSearchFileListener(project: Project) : SemanticSearchFileContentChangeListener<IndexableClass>(project) {
  override val isEnabled: Boolean
    get() = SemanticSearchSettings.getInstance().enabledInClassesTab

  override fun getStorage() = ClassEmbeddingsStorage.getInstance(project)

  override fun getEntity(id: String) = IndexableClass(id.intern())

  companion object {
    fun getInstance(project: Project): ClassesSemanticSearchFileListener = project.service()
  }
}