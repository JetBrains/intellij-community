// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.portsWatcher

import org.jetbrains.annotations.ApiStatus

/**
 * Override this interface and pass an implementation to [ProcessPortsWatcher.startWatching]
 * to receive callbacks when process starts and stops listening on a port.
 */
@ApiStatus.Experimental
interface ListeningPortHandler {
  fun onPortListeningStarted(port: ListeningPort)
  fun onPortListeningEnded(port: ListeningPort) {}
}