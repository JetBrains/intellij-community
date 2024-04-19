package org.jetbrains.plugins.notebooks.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers


@Service
class JupyterUiCoroutine(val coroutineScope: CoroutineScope) {
  // todo: reuse org.jetbrains.plugins.notebooks.jupyter.editor.outputs.webOutputs.appBasedApi.scriptLoader.utils.JupyterCoroutine
  // when the org.jetbrains.plugins.notebooks.ui module is merged with the aforementioned
  val edtScope = coroutineScope.childScope(context = Dispatchers.EDT)

  object Utils {
    val edtScope: CoroutineScope
      get() = ApplicationManager.getApplication().service<JupyterUiCoroutine>().edtScope
  }
}