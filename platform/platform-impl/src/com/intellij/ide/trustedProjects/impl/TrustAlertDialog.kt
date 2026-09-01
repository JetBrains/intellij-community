// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects.impl

import com.intellij.ide.impl.TRUSTED_PROJECTS_HELP_TOPIC
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.PopupBorder
import com.intellij.ui.WindowRoundedCornersManager
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.SwingUtilities
import javax.swing.border.Border

/**
 * A base class for the trust dialogs that copy the style of the swing alerts (see [com.intellij.ui.messages.AlertMessagesManager]).
 *
 * The chrome hides the native title bar, adds a popup border, and lets the user drag the window by its content.
 * On macOS it also places the window over the parent frame.
 *
 * A subclass renders the title as content text in [createCenterPanel].
 * It must still set [title] and then call [installAlertChrome] in its `init` block instead of [init].
 * The window must carry the title too: an undecorated window shows no title bar,
 * while accessibility and UI tests address a dialog by its title.
 */
@ApiStatus.Internal
abstract class TrustAlertDialog(project: Project?) : DialogWrapper(project) {
  @OptIn(LowLevelLocalMachineAccess::class)
  private val isTitleComponent = OS.CURRENT == OS.macOS || !Registry.`is`("ide.message.dialogs.as.swing.alert.show.title.bar", false)

  protected fun installAlertChrome() {
    @OptIn(LowLevelLocalMachineAccess::class)
    if (OS.CURRENT == OS.macOS) {
      setInitialLocationCallback {
        val rootPane: JRootPane? = SwingUtilities.getRootPane(window.parent) ?: SwingUtilities.getRootPane(window.owner)
        if (rootPane == null || !rootPane.isShowing) {
          return@setInitialLocationCallback null
        }
        val location = rootPane.locationOnScreen
        Point(location.x + (rootPane.width - window.width) / 2, (location.y + rootPane.height * 0.25).toInt())
      }
    }

    init()

    if (isTitleComponent && rootPane != null) {
      setUndecorated(true)
      rootPane.windowDecorationStyle = JRootPane.NONE
      rootPane.border = PopupBorder.Factory.create(true, true)

      object : MouseDragHelper<JComponent>(myDisposable, contentPane as JComponent) {
        var myLocation: Point? = null

        override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point): Boolean =
          dragComponent.findComponentAt(dragComponentPoint).let { target -> target == null || target == dragComponent || target is JPanel }

        override fun processDrag(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point) {
          if (myLocation == null) {
            myLocation = window.location
          }
          window.location = Point(
            myLocation!!.x + dragToScreenPoint.x - startScreenPoint.x,
            myLocation!!.y + dragToScreenPoint.y - startScreenPoint.y
          )
        }

        override fun processDragCancel() {
          myLocation = null
        }

        override fun processDragFinish(event: MouseEvent, willDragOutStart: Boolean) {
          myLocation = null
        }

        override fun processDragOutFinish(event: MouseEvent) {
          myLocation = null
        }

        override fun processDragOutCancel() {
          myLocation = null
        }

        override fun processDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point, justStarted: Boolean) {
          super.processDragOut(event, dragToScreenPoint, startScreenPoint, justStarted)
          myLocation = null
        }
      }.start()
    }

    WindowRoundedCornersManager.configure(this)
  }

  /** Creates a button action with a mnemonic and the default or focused marker. */
  protected fun alertAction(
    text: @NlsContexts.Button String,
    isDefault: Boolean = false,
    isFocused: Boolean = false,
    perform: () -> Unit,
  ): Action {
    val action = object : AbstractAction(UIUtil.replaceMnemonicAmpersand(text)) {
      override fun actionPerformed(e: ActionEvent) {
        perform()
      }
    }
    if (isDefault) {
      action.putValue(DEFAULT_ACTION, true)
    }
    if (isFocused) {
      action.putValue(FOCUSED_ACTION, true)
    }
    UIUtil.assignMnemonic(text, action)
    return action
  }

  override fun createContentPaneBorder(): Border {
    val insets = JButton().insets
    return JBUI.Borders.empty(if (isTitleComponent) 20 else 14, 20, 20 - insets.bottom, 20 - insets.right)
  }

  override fun sortActionsOnMac(actions: MutableList<Action>) {
    actions.reverse()
  }

  override fun getHelpId(): String = TRUSTED_PROJECTS_HELP_TOPIC
}
