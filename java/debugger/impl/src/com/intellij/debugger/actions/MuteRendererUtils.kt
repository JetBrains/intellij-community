// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MuteRendererUtils {
  private val MUTE_RENDERERS_FLOW = Key.create<MutableStateFlow<Boolean>>("MUTE_RENDERERS_FLOW")

  fun getOrCreateFlow(sessionData: XDebugSessionData): MutableStateFlow<Boolean> {
    return sessionData.getOrCreateUserData(MUTE_RENDERERS_FLOW) { MutableStateFlow(false) }
  }

  fun getFlow(sessionData: XDebugSessionData): MutableStateFlow<Boolean> {
    return sessionData.getUserData(MUTE_RENDERERS_FLOW)!!
  }

  @JvmStatic
  fun isMuted(sessionData: XDebugSessionData): Boolean {
    return sessionData.getUserData(MUTE_RENDERERS_FLOW)?.value ?: false
  }
}
