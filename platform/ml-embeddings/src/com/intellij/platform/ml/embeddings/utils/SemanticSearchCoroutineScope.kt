// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class SemanticSearchCoroutineScope(private val cs: CoroutineScope) : Disposable {
  override fun dispose() {}

  companion object {
    fun getScope(project: Project): CoroutineScope = project.service<SemanticSearchCoroutineScope>().cs

    fun getInstance(project: Project): SemanticSearchCoroutineScope = project.service()
  }
}