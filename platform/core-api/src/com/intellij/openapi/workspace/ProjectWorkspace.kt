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
 * A shared module can also declare a workspace service when the UI of the service runs on the backend in split mode.
 * A shared module can also declare a workspace service when it owns the UI classes of the service.
 * An example is the file editor manager in `intellij.platform.ide.impl`.
 * Constructors of workspace services, listeners, and extensions can take a [ProjectWorkspace], a `CoroutineScope`, or both in that order.
 *
 * When the last project closes, the platform disposes its project services before the workspace services.
 * When a plugin unloads, the platform disposes its workspace services before its project services.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectWorkspace : ComponentManager {
  /**
   * A snapshot of the projects in this workspace, with the host first.
   * Member projects appear after they open.
   */
  val projects: List<Project>

  /**
   * The host project of this workspace. It is the first of [projects] while the host is open.
   *
   * This property is an adapter for a legacy consumer: workspace code that calls an API that takes a `Project`.
   * New code must not use it. Use the project of the element that the code handles, or a workspace service.
   */
  @LegacyWorkspaceHostApi
  @get:ApiStatus.Internal
  val hostProject: Project
}
