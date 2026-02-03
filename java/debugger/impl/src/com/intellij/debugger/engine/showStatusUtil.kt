// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.status.StatusBarUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ShowStatusManager(private val project: Project, cs: CoroutineScope) {
  private val channel = Channel<String>(Channel.UNLIMITED)

  init {
    cs.launch {
      val statusFlow = MutableSharedFlow<String>()
      launch {
        channel.consumeEach {
          statusFlow.emit(it)
        }
      }
      @Suppress("OPT_IN_USAGE")
      statusFlow.debounce(50).collectLatest { text ->
        withContext(Dispatchers.EDT) {
          StatusBarUtil.setStatusBarInfo(project, text)
        }
      }
    }
  }

  fun showStatus(text: String) {
    channel.trySend(text)
  }
}
