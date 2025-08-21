// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared

import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionDto
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SharedJavaDebuggerSession(dto: JavaDebuggerSessionDto, private val cs: CoroutineScope) {
  private val stateFlow = dto.stateFlow.toFlow()
    .stateIn(cs, SharingStarted.Eagerly, dto.initialState)
  private val areRenderersMutedFlow = MutableStateFlow(dto.areRenderersMutedInitial)

  init {
    cs.launch {
      dto.areRenderersMutedFlow.toFlow().collect {
        areRenderersMutedFlow.value = it
      }
    }
  }

  val isAttached: Boolean get() = stateFlow.value.isAttached
  val isEvaluationPossible: Boolean get() = stateFlow.value.isEvaluationPossible

  var areRenderersMuted: Boolean
    get() = areRenderersMutedFlow.value
    set(value) {
      areRenderersMutedFlow.value = value
    }

  internal var isAsyncStacksEnabled: Boolean = true

  fun close() {
    cs.cancel()
  }

  companion object {
    @JvmStatic
    fun findSession(e: AnActionEvent): SharedJavaDebuggerSession? {
      val project = e.project ?: return null
      val sessionProxy = DebuggerUIUtil.getSessionProxy(e) ?: return null
      return SharedJavaDebuggerManager.getInstance(project).getJavaSession(sessionProxy.id)
    }
  }
}
