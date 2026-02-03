// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Recapitulates [com.intellij.jupyter.core.jupyter.editor.outputs.webOutputs.appBasedApi.scriptLoader.utils.JupyterCoroutine]
 *
 * Needed because the abovementioned class is unavailable in this module.
 */
@Service
class NotebookVisualizationCoroutine(val coroutineScope: CoroutineScope) {
  val edtScope: CoroutineScope = coroutineScope.childScope("Jupyter visualization EDT scope", context = Dispatchers.EDT)

  object Utils  {
    val edtScope: CoroutineScope
      get() = ApplicationManager.getApplication().service<NotebookVisualizationCoroutine>().edtScope

    val scope: CoroutineScope
      get() = ApplicationManager.getApplication().service<NotebookVisualizationCoroutine>().coroutineScope

    fun launchEdt(block: suspend CoroutineScope.() -> Unit): Job = edtScope.launch(block = block)

    fun launchBackground(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)
  }
}