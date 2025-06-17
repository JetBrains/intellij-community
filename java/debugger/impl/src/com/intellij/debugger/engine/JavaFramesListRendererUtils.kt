// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.XStackFrameUiPresentationContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.future.asDeferred

internal fun computeUiPresentation(
  descriptor: StackFrameDescriptorImpl?,
  selectedDescriptor: StackFrameDescriptorImpl?,
): Flow<XStackFrameUiPresentationContainer> = channelFlow {
  send(XStackFrameUiPresentationContainer().apply {
    append(JavaDebuggerBundle.message("frame.panel.computing.frame"), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
  })
  val container = XStackFrameUiPresentationContainer()

  descriptor?.let { descriptor ->
    val context = (descriptor.debugProcess as DebugProcessImpl).debuggerContext.suspendContext ?: return@let
    try {
      withDebugContext(context, PrioritizedTask.Priority.HIGH) {
        descriptor.updateRepresentationNoNotify(null) {}
      }
    }
    catch (e: Exception) {
      close(e)
      throw e
    }
    JavaFramesListRenderer.customizePresentation(descriptor, container, selectedDescriptor)
    send(container)
  }

  if (descriptor == null || selectedDescriptor == null) {
    close()
    return@channelFlow
  }
  val method = descriptor.method
  if (method != selectedDescriptor.method) {
    close()
    return@channelFlow
  }

  descriptor.exactRecursiveIndex.asDeferred().await()?.let { index ->
    if (index > 0) {
      send(container.copy().apply {
        append(" [$index]", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
      })
    }
  }
  close()
}.buffer(Channel.CONFLATED)
