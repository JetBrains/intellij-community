// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared

import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SharedJavaDebuggerSession(dto: JavaDebuggerSessionDto, private val cs: CoroutineScope) {
  private val stateFlow = dto.stateFlow.toFlow()
    .stateIn(cs, SharingStarted.Eagerly, dto.initialState)

  val isAttached: Boolean get() = stateFlow.value.isAttached
  val isEvaluationPossible: Boolean get() = stateFlow.value.isEvaluationPossible

  internal var isAsyncStacksEnabled: Boolean = true

  fun close() {
    cs.cancel()
  }
}
