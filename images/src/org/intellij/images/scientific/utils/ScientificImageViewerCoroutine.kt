package org.intellij.images.scientific.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Service
class ScientificImageViewerCoroutine(val coroutineScope: CoroutineScope) {

  object Utils {
    val scope: CoroutineScope
      get() = ApplicationManager.getApplication().service<ScientificImageViewerCoroutine>().coroutineScope
  }
}

fun launchBackground(block: suspend CoroutineScope.() -> Unit): Job = ScientificImageViewerCoroutine.Utils.scope.launch(block = block)