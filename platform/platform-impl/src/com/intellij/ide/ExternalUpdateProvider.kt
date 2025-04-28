// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * Provider for IDE updates information.
 */
@ApiStatus.Internal
interface ExternalUpdateProvider {

  val updatesState: ExternalUpdateState

  val updatesStateFlow: StateFlow<ExternalUpdateState>

  fun runUpdate()

}

sealed interface ExternalUpdateState {
  object Preparing : ExternalUpdateState
  object NoUpdates : ExternalUpdateState
  object RestartNeeded : ExternalUpdateState
  data class UpdateAvailable(val info: IdeUpdateInfo) : ExternalUpdateState
}

@ApiStatus.Internal
data class IdeUpdateInfo(
  val fullName: String,
  val version: String,
)