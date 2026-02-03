// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.actionSystem.AnAction
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * Provider for IDE updates information.
 */
@ApiStatus.Internal
interface ExternalUpdateProvider {

  val updatesState: ExternalUpdateState

  val updatesStateFlow: StateFlow<ExternalUpdateState>

  fun runUpdate()

}

@ApiStatus.Internal
sealed interface ExternalUpdateState {
  val info: IdeUpdateInfo?

  object NoUpdates : ExternalUpdateState {
    override val info = null
  }

  data class Available(override val info: IdeUpdateInfo) : ExternalUpdateState
  data class Downloading(override val info: IdeUpdateInfo) : ExternalUpdateState
  data class ReadyToInstall(override val info: IdeUpdateInfo) : ExternalUpdateState
}

@ApiStatus.Internal
data class IdeUpdateInfo(
  val icon: Icon,
  val action: AnAction,
)