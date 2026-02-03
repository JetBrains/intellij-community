// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

/**
 * The service is intended to be used instead of a project/application as a parent disposable.
 */
@Service(Service.Level.APP, Service.Level.PROJECT)
class NotebookPluginDisposable(coroutineScope: CoroutineScope) : Disposable, CoroutineScope by coroutineScope {
  override fun dispose(): Unit = Unit

  companion object {
    fun getInstance(): NotebookPluginDisposable = service()

    fun getInstance(project: Project?): Disposable {
      return project?.getService(NotebookPluginDisposable::class.java) ?: getInstance()
    }
  }
}