// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.BalloonBuilder
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.HyperlinkListener

/**
 * If you want to register a toolwindow, which will be enabled during the dumb mode, please use [ToolWindowManager]'s
 * registration methods which have 'canWorkInDumbMode' parameter.
 *
 * @see com.intellij.openapi.wm.ex.ToolWindowManagerListener
 */
abstract class ToolWindowManager {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ToolWindowManager = project.getService(ToolWindowManager::class.java)
  }

  abstract val focusManager: IdeFocusManager

  abstract fun canShowNotification(toolWindowId: String): Boolean

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use ToolWindowFactory and toolWindow extension point")
  fun registerToolWindow(id: String, component: JComponent, anchor: ToolWindowAnchor): ToolWindow {
    return registerToolWindow(RegisterToolWindowTask(id = id, component = component, anchor = anchor, canCloseContent = false, canWorkInDumbMode = false))
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use ToolWindowFactory and toolWindow extension point")
  fun registerToolWindow(id: String, canCloseContent: Boolean, anchor: ToolWindowAnchor): ToolWindow {
    return registerToolWindow(RegisterToolWindowTask(id = id, anchor = anchor, canCloseContent = canCloseContent, canWorkInDumbMode = false))
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use ToolWindowFactory and toolWindow extension point")
  fun registerToolWindow(id: String,
                         canCloseContent: Boolean,
                         anchor: ToolWindowAnchor,
                         secondary: Boolean): ToolWindow {
    return registerToolWindow(RegisterToolWindowTask(id = id, anchor = anchor, sideTool = secondary, canCloseContent = canCloseContent, canWorkInDumbMode = false))
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use ToolWindowFactory and toolWindow extension point")
  fun registerToolWindow(id: String,
                         canCloseContent: Boolean,
                         anchor: ToolWindowAnchor,
                         @Suppress("UNUSED_PARAMETER") parentDisposable: Disposable,
                         canWorkInDumbMode: Boolean): ToolWindow {
    return registerToolWindow(RegisterToolWindowTask(id = id, anchor = anchor, canCloseContent = canCloseContent, canWorkInDumbMode = canWorkInDumbMode))
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use ToolWindowFactory and toolWindow extension point")
  fun registerToolWindow(id: String,
                         canCloseContent: Boolean,
                         anchor: ToolWindowAnchor,
                         @Suppress("UNUSED_PARAMETER") parentDisposable: Disposable,
                         canWorkInDumbMode: Boolean,
                         secondary: Boolean): ToolWindow {
    return registerToolWindow(RegisterToolWindowTask(id = id, anchor = anchor, sideTool = secondary, canCloseContent = canCloseContent, canWorkInDumbMode = canWorkInDumbMode))
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use ToolWindowFactory and toolWindow extension point")
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  fun registerToolWindow(id: String,
                         canCloseContent: Boolean,
                         anchor: ToolWindowAnchor,
                         @Suppress("UNUSED_PARAMETER") parentDisposable: Disposable): ToolWindow {
    return registerToolWindow(RegisterToolWindowTask(id = id, anchor = anchor, canCloseContent = canCloseContent, canWorkInDumbMode = false))
  }

  @ApiStatus.Internal
  abstract fun registerToolWindow(task: RegisterToolWindowTask): ToolWindow

  /**
   * [ToolWindow.getAnchor] is set to [ToolWindowAnchor.BOTTOM] by default.
   * [ToolWindow.setToHideOnEmptyContent] is set to `true` by default.
   */
  @ApiStatus.Experimental
  @ApiStatus.Internal
  inline fun registerToolWindow(id: String, builder: RegisterToolWindowTaskBuilder.() -> Unit): ToolWindow {
    val b = RegisterToolWindowTaskBuilder(id)
    b.builder()
    return registerToolWindow(b.build())
  }

  /**
   * does nothing if tool window with specified isn't registered.
   */
  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use ToolWindowFactory and toolWindow extension point")
  abstract fun unregisterToolWindow(id: String)

  abstract fun activateEditorComponent()

  /**
   * @return `true` if and only if editor component is active.
   */
  abstract val isEditorComponentActive: Boolean

  /**
   * @return array of `id`s of all registered tool windows.
   */
  abstract val toolWindowIds: Array<String>

  abstract val toolWindowIdSet: Set<String>

  /**
   * @return `ID` of currently active tool window or `null` if there is no active
   * tool window.
   */
  abstract val activeToolWindowId: String?

  /**
   * @return `ID` of tool window that was activated last time.
   */
  abstract val lastActiveToolWindowId: String?

  /**
   * @return registered tool window with specified `id`. If there is no registered
   * tool window with specified `id` then the method returns `null`.
   * @see ToolWindowId
   */
  abstract fun getToolWindow(@NonNls id: String?): ToolWindow?

  /**
   * Puts specified runnable to the tail of current command queue.
   */
  abstract fun invokeLater(runnable: Runnable)

  fun notifyByBalloon(toolWindowId: String, type: MessageType, @NlsContexts.NotificationContent htmlBody: String) {
    @Suppress("SSBasedInspection")
    notifyByBalloon(ToolWindowBalloonShowOptions(toolWindowId = toolWindowId,
                                                 type = type,
                                                 htmlBody = htmlBody,
                                                 icon = null,
                                                 listener = null))
  }

  fun notifyByBalloon(toolWindowId: String,
                      type: MessageType,
                      @NlsContexts.PopupContent htmlBody: String,
                      icon: Icon?,
                      listener: HyperlinkListener?) {
    @Suppress("SSBasedInspection")
    notifyByBalloon(ToolWindowBalloonShowOptions(toolWindowId = toolWindowId, type = type, htmlBody = htmlBody, icon = icon, listener = listener))
  }

  @Suppress("SSBasedInspection")
  abstract fun notifyByBalloon(options: ToolWindowBalloonShowOptions)

  abstract fun getToolWindowBalloon(id: String): Balloon?

  abstract fun isMaximized(window: ToolWindow): Boolean

  abstract fun setMaximized(window: ToolWindow, maximized: Boolean)

  /*
   * Returns visual representation of tool window location
   * @see AllIcons.Actions#MoveToBottomLeft ... com.intellij.icons.AllIcons.Actions#MoveToWindow icon set
   */
  open fun getLocationIcon(id: String, fallbackIcon: Icon): Icon = fallbackIcon
}

@ApiStatus.Internal
class RegisterToolWindowTaskBuilder @PublishedApi internal constructor(private val id: String) {
  @JvmField
  var anchor = ToolWindowAnchor.BOTTOM
  @JvmField
  var stripeTitle: Supplier<@NlsContexts.TabTitle String>? = null
  @JvmField
  var icon: Icon? = null
  @JvmField
  var shouldBeAvailable: Boolean = true
  @JvmField
  var canCloseContent: Boolean = true
  @JvmField
  var hideOnEmptyContent: Boolean = true
  @JvmField
  var sideTool: Boolean = false

  @JvmField
  var contentFactory: ToolWindowFactory? = null

  @PublishedApi
  internal fun build(): RegisterToolWindowTask {
    val result = RegisterToolWindowTask(
      id = id,
      anchor = anchor,
      stripeTitle = stripeTitle,
      icon = icon,
      canCloseContent = canCloseContent,
      shouldBeAvailable = shouldBeAvailable,
      sideTool = sideTool,
      contentFactory = contentFactory,
    )
    result.hideOnEmptyContent = hideOnEmptyContent
    return result
  }
}

data class ToolWindowBalloonShowOptions(val toolWindowId: String,
                                        val type: MessageType,
                                        @NlsContexts.PopupContent val htmlBody: String,
                                        val icon: Icon? = null,
                                        val listener: HyperlinkListener? = null,
                                        val balloonCustomizer: Consumer<BalloonBuilder>? = null)