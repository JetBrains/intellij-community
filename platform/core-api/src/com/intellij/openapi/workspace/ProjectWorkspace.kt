// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.workspace

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Thread-safe service container shared by a workspace host and its member projects.
 * A standalone project has its own workspace. Use [Project.workspace] to access it.
 *
 * Register a workspace service with `<workspaceService>` or `@Service(Service.Level.WORKSPACE)`
 * in a module that depends on `intellij.platform.frontend`.
 * Constructors of workspace services, listeners, and extensions can take a [ProjectWorkspace], a `CoroutineScope`, or both in that order.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectWorkspace : ComponentManager {
  /**
   * A snapshot of the projects in this workspace, with the host first.
   * Member projects appear after they open.
   */
  val projects: List<Project>
}
