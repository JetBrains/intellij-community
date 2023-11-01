// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import org.jetbrains.annotations.ApiStatus.Experimental

private val LocalPreviewKeyEventHost = compositionLocalOf<PreviewKeyEventHost> { error("LocalPreviewKeyEventHost is not provided") }

@Composable
@Experimental
fun PreviewKeyEventHost(content: @Composable () -> Unit) {
  val host = remember { PreviewKeyEventHost() }
  CompositionLocalProvider(LocalPreviewKeyEventHost provides host) {
    Box(modifier = Modifier.onPreviewKeyEvent {
      host.handlePreviewKeyEvent(it)
    }) {
      content()
    }
  }
}

@Experimental
fun Modifier.onHostPreviewKeyEvent(
  enabled: Boolean,
  onPreviewKeyEvent: (KeyEvent) -> Boolean
) = composed {
  // TODO: rewrite to Modifier.Node
  if (!enabled) {
    return@composed Modifier
  }
  val host = LocalPreviewKeyEventHost.current
  val _onPreviewKeyEvent = rememberUpdatedState(onPreviewKeyEvent)
  DisposableEffect(host, _onPreviewKeyEvent) {
    val handler: (KeyEvent) -> Boolean = { event ->
      _onPreviewKeyEvent.value(event)
    }
    host.addEventHandler(handler)
    onDispose {
      host.removeEventHandler(handler)
    }
  }
  Modifier
}

private class PreviewKeyEventHost {
  private val handlers = mutableListOf<(KeyEvent) -> Boolean>()

  fun addEventHandler(onPreviewKeyEvent: (KeyEvent) -> Boolean) {
    handlers.add(onPreviewKeyEvent)
  }

  fun removeEventHandler(onPreviewKeyEvent: (KeyEvent) -> Boolean) {
    handlers.remove(onPreviewKeyEvent)
  }

  fun handlePreviewKeyEvent(keyEvent: KeyEvent): Boolean {
    for (handler in handlers) {
      val result = handler.invoke(keyEvent)
      if (result) {
        return true
      }
    }
    return false
  }
}