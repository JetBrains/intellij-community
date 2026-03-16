// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service
@State(name = "ExceptionEAPAutoReportManager",
       storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED, usePathMacroManager = false)])
internal class ExceptionEAPAutoReportManager : SerializablePersistentStateComponent<ExceptionEAPAutoReportManager.State>(State()) {

  companion object {
    fun getInstance(): ExceptionEAPAutoReportManager = service<ExceptionEAPAutoReportManager>()
  }

  data class State(
    @JvmField @Attribute val isEnabledInEAP: Boolean = true,
  )

  var enabledInEAP: Boolean
    get() = state.isEnabledInEAP
    set(value) {
      updateState {
        it.copy(isEnabledInEAP = value)
      }
    }
}