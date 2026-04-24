// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.portsWatcher

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface ListeningPort {
  val port: Int
  /**
   * ID of the process that is listening on this port.
   * Sometimes we are unable to detect it (if stdout listener is used)
   */
  val pid: Long?
}

@ApiStatus.Internal
data class ListeningPortImpl(
  override val port: Int,
  override val pid: Long?,
) : ListeningPort