// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Sends the live state of a Run tool window icon to the clients.
 *
 * The `intellij.platform.execution.rpc` module registers the implementation.
 * [RunContentManagerImpl] does nothing when no implementation is registered.
 */
@ApiStatus.Internal
interface RunContentLiveIconPublisher {
  fun publish(project: Project, toolWindowId: String, alive: Boolean)
}
