// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared

import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionDto
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.debugger.impl.shared.FrontendDescriptorStateManager
import com.intellij.platform.debugger.impl.shared.FrontendDescriptorStateManagerExtension
import com.intellij.xdebugger.frame.XDescriptor
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SharedJavaDebuggerSession(dto: JavaDebuggerSessionDto, cs: CoroutineScope) {
  private val _state = MutableStateFlow(dto.initialState)
  private val _areRenderersMuted = MutableStateFlow(dto.areRenderersMutedInitial)

  init {
    cs.launch { dto.stateFlow.toFlow().collectLatest { _state.value = it } }
    cs.launch { dto.areRenderersMutedFlow.toFlow().collectLatest { _areRenderersMuted.value = it } }
  }

  val isAttached: Boolean get() = _state.value.isAttached
  val isEvaluationPossible: Boolean get() = _state.value.isEvaluationPossible

  var areRenderersMuted: Boolean
    get() = _areRenderersMuted.value
    set(value) { _areRenderersMuted.value = value }

  internal var isAsyncStacksEnabled: Boolean = true

  companion object {
    @JvmStatic
    fun findSession(e: AnActionEvent): SharedJavaDebuggerSession? {
      val project = e.project ?: return null
      val sessionProxy = DebuggerUIUtil.getSessionProxy(e) ?: return null
      return FrontendDescriptorStateManager.getInstance(project).getProcessDescriptorState(sessionProxy.id) as? SharedJavaDebuggerSession
    }
  }
}

internal class JavaDebuggerSessionDtoStateExtension : FrontendDescriptorStateManagerExtension {
  override fun createState(descriptor: XDescriptor, cs: CoroutineScope): Any? {
    if (descriptor !is JavaDebuggerSessionDto) return null
    return SharedJavaDebuggerSession(descriptor, cs)
  }
}

