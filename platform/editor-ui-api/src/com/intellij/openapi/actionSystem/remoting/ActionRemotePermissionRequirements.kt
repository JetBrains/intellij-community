// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.remoting

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.NlsActions.ActionDescription
import com.intellij.openapi.util.NlsActions.ActionText
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * Marker interface which defines which permissions
 * an action require to be available in remote development mode
 */
@ApiStatus.Internal
@ApiStatus.Experimental
interface ActionRemotePermissionRequirements {
  /**
   * The action requires run access.
   *
   * Examples: starting a process or run configuration, debugger actions
   */
  interface RunAccess : ActionRemotePermissionRequirements

  /**
   * The action requires read access.
   *
   * Examples: opening an editor or creating a document with some file content,
   * showing a list of run configurations, getting bookmarks
   */
  interface ReadAccess : ActionRemotePermissionRequirements

  /**
   * The action requires write access.
   *
   * Examples: Refactorings, editor actions, editing or removing run configurations,
   * toggling bookmarks
   */
  interface WriteAccess : ActionRemotePermissionRequirements

  // Base classes to use as a parent for anonymous classes in Java

  @ApiStatus.Internal
  @ApiStatus.Experimental
  abstract class ActionWithRunAccess(@ActionText text: String? = null, @ActionDescription description: String? = null, icon: Icon? = null)
    : AnAction(text, description, icon), RunAccess

  @ApiStatus.Internal
  @ApiStatus.Experimental
  abstract class ActionWithReadAccess(@ActionText text: String? = null, @ActionDescription description: String? = null, icon: Icon? = null)
    : AnAction(text, description, icon), ReadAccess

  @ApiStatus.Internal
  @ApiStatus.Experimental
  abstract class ActionWithWriteAccess(@ActionText text: String? = null, @ActionDescription description: String? = null, icon: Icon? = null)
    : AnAction(text, description, icon), WriteAccess
}