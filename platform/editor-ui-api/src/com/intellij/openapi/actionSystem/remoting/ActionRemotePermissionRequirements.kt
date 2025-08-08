// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.remoting

import org.jetbrains.annotations.ApiStatus

/**
 * Marker interface which defines which permissions
 * an action require to be available in remote development mode
 */
@ApiStatus.Internal
interface ActionRemotePermissionRequirements {
  /**
   * The action requires run access.
   *
   * Examples: starting a process or run configuration, debugger actions
   */
  @Deprecated("Use RequiresPermissions instead")
  interface RunAccess : ActionRemotePermissionRequirements

  /**
   * The action requires read access.
   *
   * Examples: opening an editor or creating a document with some file content,
   * showing a list of run configurations, getting bookmarks
   */
  @Deprecated("Use RequiresPermissions instead")
  interface ReadAccess : ActionRemotePermissionRequirements

  /**
   * The action requires write access.
   *
   * Examples: Refactorings, editor actions, editing or removing run configurations,
   * toggling bookmarks
   */
  @Deprecated("Use RequiresPermissions instead")
  interface WriteAccess : ActionRemotePermissionRequirements
}