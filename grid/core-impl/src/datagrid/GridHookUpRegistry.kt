package com.intellij.database.datagrid

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Registers hookups of a project, so code below the grid UI module can register one.
 * The only implementation is `GridDataHookUpManager` in the grid UI module.
 * It parks the hookups of editors that close to reopen, which reads `FileEditorManagerKeys` from `intellij.platform.ide.impl`.
 */
@ApiStatus.Internal
interface GridHookUpRegistry {
  /** Registers [hookUp] under [parent] and returns it. A [Disposable] hookup is disposed when its last parent is disposed. */
  fun <T : GridDataHookUp<GridRow, GridColumn>> registerHookUp(hookUp: T, parent: Disposable): T

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GridHookUpRegistry = project.service()
  }
}
