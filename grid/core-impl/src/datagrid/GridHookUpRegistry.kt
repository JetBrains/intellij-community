package com.intellij.database.datagrid

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Registers hookups of a project, so code below the grid UI module can register one.
 * The only implementation is `GridDataHookUpManager` in the grid UI module.
 * It keeps the hookup of an editor that says, through its [EditorOwner], that it closes to reopen.
 */
@ApiStatus.Internal
interface GridHookUpRegistry {
  /** Registers [hookUp] under [parent] and returns it. A [Disposable] hookup is disposed when its last parent is disposed. */
  fun <T : GridDataHookUp<GridRow, GridColumn>> registerHookUp(hookUp: T, parent: Disposable): T

  /**
   * An editor's reference to [hookUp]. With `keepForReopen`, [release] keeps the hookup for a tab move.
   * The editor calls [release] first in its own `dispose()`, because only the editor knows whether it closes to reopen.
   * The owner isn't a Disposer child of the editor: children are disposed before the editor's `dispose()`, without the keep request.
   * Nor is it a child of the registry: closing a project closes only the focused window's tabs before the registry is disposed.
   */
  interface EditorOwner<out H : GridDataHookUp<GridRow, GridColumn>> {
    val hookUp: H

    /**
     * Releases the reference of this owner.
     * With [keepForReopen], a hookup whose last reference this was stays registered until an editor acquires it again.
     * The project's close disposes it otherwise.
     * The flag applies to this reference only: a hookup that a plain parent still holds is disposed with that parent.
     * A second release does nothing.
     */
    fun release(keepForReopen: Boolean)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GridHookUpRegistry = project.service()
  }
}
