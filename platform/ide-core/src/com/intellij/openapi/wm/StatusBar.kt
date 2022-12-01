// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.StatusBar.Info
import com.intellij.openapi.wm.StatusBar.StandardWidgets
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Color
import java.awt.Component
import javax.swing.JComponent

/**
 * Status bar shown on the bottom of IDE frame.
 *
 * Displays [status text][Info.set] and a number of [builtin][StandardWidgets] and custom [widgets][StatusBarWidget].
 *
 * @see com.intellij.openapi.wm.StatusBarWidgetFactory
 */
interface StatusBar : StatusBarInfo {
  object Info {
    @Topic.ProjectLevel
    @JvmField
    val TOPIC = Topic("IdeStatusBar.Text", StatusBarInfo::class.java, Topic.BroadcastDirection.NONE)

    @JvmOverloads
    @JvmStatic
    fun set(text: @NlsContexts.StatusBarText String?, project: Project?, requestor: @NonNls String? = null) {
      if (project != null) {
        if (project.isDisposed) {
          return
        }
        if (!project.isInitialized) {
          StartupManager.getInstance(project).runAfterOpened { project.messageBus.syncPublisher(TOPIC).setInfo(text, requestor) }
          return
        }
      }
      (project?.messageBus ?: ApplicationManager.getApplication().messageBus).syncPublisher(TOPIC).setInfo(text, requestor)
    }
  }

  /**
   * Adds the given widget on the right.
   *
   */
  @Deprecated("Use {@link StatusBarWidgetFactory}")
  fun addWidget(widget: StatusBarWidget)

  /**
   * Adds the given widget positioned according to given anchor (see [Anchors]).
   *
   */
  @Deprecated("Use {@link StatusBarWidgetFactory}")
  fun addWidget(widget: StatusBarWidget, anchor: @NonNls String)

  /**
   * Adds the given widget on the right.
   *
   * For external usages use [com.intellij.openapi.wm.StatusBarWidgetFactory].
   */
  @ApiStatus.Internal
  fun addWidget(widget: StatusBarWidget, parentDisposable: Disposable)

  /**
   * Adds the given widget positioned according to given anchor (see [Anchors]).
   *
   * For external usages use [com.intellij.openapi.wm.StatusBarWidgetFactory].
   */
  @ApiStatus.Internal
  fun addWidget(widget: StatusBarWidget, anchor: @NonNls String, parentDisposable: Disposable)

  /**
   * For external usages use [com.intellij.openapi.wm.StatusBarWidgetFactory].
   */
  @ApiStatus.Internal
  fun removeWidget(id: @NonNls String)

  fun updateWidget(id: @NonNls String)

  fun getWidget(id: @NonNls String): StatusBarWidget?

  fun fireNotificationPopup(content: JComponent, backgroundColor: Color?)

  @ApiStatus.Internal
  fun createChild(frame: IdeFrame, editorProvider: () -> FileEditor?): StatusBar?

  val component: JComponent?

  fun findChild(c: Component): StatusBar?

  val project: Project?

  object Anchors {
    @JvmField
    val DEFAULT_ANCHOR = after(StandardWidgets.COLUMN_SELECTION_MODE_PANEL)

    @JvmStatic
    fun before(widgetId: String): String = "before $widgetId"

    @JvmStatic
    fun after(widgetId: String): String = "after $widgetId"
  }

  object StandardWidgets {
    const val ENCODING_PANEL = "Encoding"
    // keep the old ID for backwards compatibility
    const val COLUMN_SELECTION_MODE_PANEL = "InsertOverwrite"
    const val READONLY_ATTRIBUTE_PANEL = "ReadOnlyAttribute"
    const val POSITION_PANEL = "Position"
    const val LINE_SEPARATOR_PANEL = "LineSeparator"
  }

  fun startRefreshIndication(tooltipText: @NlsContexts.Tooltip String?)

  fun stopRefreshIndication()

  fun addListener(listener: StatusBarListener, parentDisposable: Disposable) {}

  val allWidgets: Collection<StatusBarWidget>?
    get() = null

  fun getWidgetAnchor(id: @NonNls String): @NonNls String? = null

  /**
   * if not `null`, an editor which should be used as the current one
   * by editor-based widgets installed on this status bar, otherwise should be ignored.
   */
  @get:ApiStatus.Internal
  @get:ApiStatus.Experimental

  val currentEditor: () -> FileEditor?
}