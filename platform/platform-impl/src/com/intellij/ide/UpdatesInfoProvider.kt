// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * Provider for IDE updates information.
 */
@ApiStatus.Internal
interface UpdatesInfoProvider {

  val updateInfo: IdeUpdateInfo?

  val updateAvailable: Boolean

  fun runUpdate(updateInfo: IdeUpdateInfo)

}

@ApiStatus.Internal
data class IdeUpdateInfo(
  val fullName: String,
  val version: String,
)