// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ComponentUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Window
import javax.swing.JComponent

/**
 * A Search Everywhere input field that a toolbar hosts.
 *
 * When the Search Everywhere action runs in a window that shows such a field, the field receives the focus.
 * The results popup then opens under the field instead of the standard popup.
 */
@ApiStatus.Internal
interface SearchEverywhereToolbarField {
  /** The text field. The results popup is anchored to it. */
  val component: JComponent

  /** True when the field is on the screen and can accept the focus. */
  val isAvailable: Boolean
    get() = component.isShowing && component.isEnabled

  companion object {
    @JvmField
    val DATA_KEY: DataKey<SearchEverywhereToolbarField> = DataKey.create("search.everywhere.toolbar.field")
  }
}

/**
 * The registry of the toolbar fields that are on the screen.
 *
 * A field registers itself in `addNotify` and unregisters itself in `removeNotify`.
 */
@ApiStatus.Internal
object SearchEverywhereToolbarFields {
  private val fields = ContainerUtil.createLockFreeCopyOnWriteList<SearchEverywhereToolbarField>()

  fun register(field: SearchEverywhereToolbarField) {
    if (!fields.contains(field)) {
      fields.add(field)
    }
  }

  fun unregister(field: SearchEverywhereToolbarField) {
    fields.remove(field)
  }

  /** Returns an available field whose window is [window], or null. */
  fun findIn(window: Window?): SearchEverywhereToolbarField? {
    return fields.firstOrNull { field ->
      field.isAvailable && ComponentUtil.getWindow(field.component) === window
    }
  }

  /**
   * Returns [event] with the toolbar field of its window in the data context.
   * Returns [event] itself when the data context already holds a field or when the window has no field.
   */
  @JvmStatic
  fun withToolbarField(event: AnActionEvent): AnActionEvent {
    if (event.getData(SearchEverywhereToolbarField.DATA_KEY) != null) {
      return event
    }
    val field = findIn(windowOf(event)) ?: return event
    val dataContext = CustomizedDataContext.withSnapshot(event.dataContext) { sink ->
      sink[SearchEverywhereToolbarField.DATA_KEY] = field
    }
    return event.withDataContext(dataContext)
  }

  private fun windowOf(event: AnActionEvent): Window? {
    val contextWindow = ComponentUtil.getWindow(event.getData(PlatformDataKeys.CONTEXT_COMPONENT))
    if (contextWindow != null) {
      return contextWindow
    }
    val project = event.project ?: return null
    return WindowManager.getInstance().getFrame(project)
  }
}
