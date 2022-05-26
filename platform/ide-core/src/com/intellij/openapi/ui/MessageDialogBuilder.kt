// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui

import com.intellij.CommonBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageConstants.*
import com.intellij.openapi.ui.messages.MessagesService
import com.intellij.openapi.ui.messages.MessagesService.Companion.getInstance
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ComponentUtil
import com.intellij.ui.mac.MacMessages
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.awt.Window
import javax.swing.Icon

sealed class MessageDialogBuilder<T : MessageDialogBuilder<T>>(protected val title: @NlsContexts.DialogTitle String,
                                                               protected val message: @DialogMessage String) {
  protected var yesText: String? = null
  protected var noText: String? = null

  protected var project: Project? = null
  protected var parentComponent: Component? = null

  protected var icon: Icon? = null
  protected var doNotAskOption: DoNotAskOption? = null
  @NonNls protected var helpId: String? = null

  protected abstract fun getThis(): T

  companion object {
    @JvmStatic
    fun yesNo(title: @NlsContexts.DialogTitle String, message: @DialogMessage String): YesNo {
      return YesNo(title, message).icon(UIUtil.getQuestionIcon())
    }

    @JvmStatic
    fun yesNo(title: @NlsContexts.DialogTitle String, message: @DialogMessage String, icon: Icon?): YesNo {
      return YesNo(title, message).icon(icon)
    }

    @JvmStatic
    fun okCancel(title: @NlsContexts.DialogTitle String, message: @DialogMessage String): OkCancelDialogBuilder {
      return OkCancelDialogBuilder(title, message).icon(UIUtil.getQuestionIcon())
    }

    @JvmStatic
    fun yesNoCancel(title: @NlsContexts.DialogTitle String, message: @DialogMessage String): YesNoCancel {
      return YesNoCancel(title, message).icon(UIUtil.getQuestionIcon())
    }
  }

  /**
   * @see asWarning
   * @see UIUtil.getInformationIcon
   * @see UIUtil.getWarningIcon
   * @see UIUtil.getErrorIcon
   * @see UIUtil.getQuestionIcon
   */
  fun icon(icon: Icon?): T {
    this.icon = icon
    return getThis()
  }

  fun asWarning(): T {
    icon = UIUtil.getWarningIcon()
    return getThis()
  }

  fun doNotAsk(doNotAskOption: DoNotAskOption?): T {
    this.doNotAskOption = doNotAskOption
    return getThis()
  }

  fun yesText(yesText: @NlsContexts.Button String): T {
    this.yesText = yesText
    return getThis()
  }

  fun noText(noText: @NlsContexts.Button String): T {
    this.noText = noText
    return getThis()
  }

  fun help(@NonNls helpId: String): T {
    this.helpId = helpId
    return getThis()
  }

  class YesNo internal constructor(title: String, message: String) : MessageDialogBuilder<YesNo>(title, message) {
    override fun getThis() = this

    fun ask(project: Project?) = show(project = project, parentComponent = null)

    fun ask(parentComponent: Component?) = show(project = null, parentComponent = parentComponent)

    /**
     * Use this method only if you know neither project nor component.
     */
    fun guessWindowAndAsk() = show(project = null, parentComponent = null)

    @Deprecated(message = "Use ask(project)", level = DeprecationLevel.ERROR)
    fun isYes(): Boolean = show(project = null, parentComponent = null)

    @Deprecated(message = "Use ask(project)", level = DeprecationLevel.ERROR)
    fun show(): Int = if (show(project = project, parentComponent = parentComponent)) YES else NO

    @YesNoResult
    private fun show(project: Project?, parentComponent: Component?): Boolean {
      val yesText = yesText ?: CommonBundle.getYesButtonText()
      val noText = noText ?: CommonBundle.getNoButtonText()
      return showMessage(
        project,
        parentComponent,
        mac = { MacMessages.getInstance().showYesNoDialog(title, message, yesText, noText, it, doNotAskOption, icon, helpId) },
        other = {
          MessagesService.getInstance().showMessageDialog(
            project = project, parentComponent = parentComponent, message = message, title = title, icon = icon,
            options = arrayOf(yesText, noText), doNotAskOption = doNotAskOption, helpId = helpId, alwaysUseIdeaUI = true
          ) == YES
        }
      )
    }
  }

  class YesNoCancel internal constructor(title: String, message: String) : MessageDialogBuilder<YesNoCancel>(title, message) {
    @NlsContexts.Button private var cancelText: String? = null

    fun cancelText(cancelText: @NlsContexts.Button String): YesNoCancel {
      this.cancelText = cancelText
      return getThis()
    }

    override fun getThis() = this

    @YesNoCancelResult
    fun show(project: Project?) = show(project = project, parentComponent = null)

    @YesNoCancelResult
    fun show(parentComponent: Component?) = show(project = null, parentComponent = parentComponent)

    @YesNoCancelResult
    fun guessWindowAndAsk() = show(project = null, parentComponent = null)

    @YesNoCancelResult
    private fun show(project: Project?, parentComponent: Component?): Int {
      val yesText = yesText ?: CommonBundle.getYesButtonText()
      val noText = noText ?: CommonBundle.getNoButtonText()
      val cancelText = cancelText ?: CommonBundle.getCancelButtonText()
      return showMessage(project, parentComponent, mac = { window ->
        MacMessages.getInstance().showYesNoCancelDialog(title, message, yesText, noText, cancelText, window, doNotAskOption, icon, helpId)
      }, other = {
        val options = arrayOf(yesText, noText, cancelText)
        when (MessagesService.getInstance().showMessageDialog(project = project, parentComponent = parentComponent,
                                                              message = message, title = title, options = options,
                                                              icon = icon,
                                                              doNotAskOption = doNotAskOption,
                                                              helpId = helpId, alwaysUseIdeaUI = true)) {
          0 -> YES
          1 -> NO
          else -> CANCEL
        }
      })
    }
  }

  @ApiStatus.Experimental
  class Message(title: String, message: String) : MessageDialogBuilder<Message>(title, message) {
    private lateinit var buttons: List<String>
    private var defaultButton: String? = null
    private var focusedButton: String? = null

    override fun getThis() = this

    fun buttons(vararg buttonNames: String): Message {
      buttons = buttonNames.toList()
      return this
    }

    fun defaultButton(defaultButtonName: String): Message {
      defaultButton = defaultButtonName
      return this
    }

    fun focusedButton(focusedButtonName: String): Message {
      focusedButton = focusedButtonName
      return this
    }

    fun show(project: Project? = null, parentComponent: Component? = null): String? {
      val options = buttons.toTypedArray()
      val defaultOptionIndex = buttons.indexOf(defaultButton)
      val focusedOptionIndex = buttons.indexOf(focusedButton)
      val result = showMessage(project, parentComponent, mac = { window ->
        MacMessages.getInstance().showMessageDialog(title, message, options, window, defaultOptionIndex, focusedOptionIndex,
                                                    doNotAskOption, icon, helpId)
      }, other = {
        MessagesService.getInstance().showMessageDialog(project = project, parentComponent = parentComponent, message = message,
                                                        title = title, options = options,
                                                        defaultOptionIndex = defaultOptionIndex, focusedOptionIndex = focusedOptionIndex,
                                                        icon = icon, doNotAskOption = doNotAskOption, helpId = helpId,
                                                        alwaysUseIdeaUI = true)
      })
      return if (result < 0) null else buttons[result]
    }
  }
}

class OkCancelDialogBuilder internal constructor(title: String, message: String) : MessageDialogBuilder<OkCancelDialogBuilder>(title, message) {
  override fun getThis() = this

  fun ask(project: Project?) = show(project = project, parentComponent = null)

  fun ask(parentComponent: Component?) = show(project = null, parentComponent = parentComponent)

  /**
   * Use this method only if you know neither project nor component.
   */
  fun guessWindowAndAsk() = show(project = null, parentComponent = null)

  private fun show(project: Project?, parentComponent: Component?): Boolean {
    val yesText = yesText ?: CommonBundle.getOkButtonText()
    val noText = noText ?: CommonBundle.getCancelButtonText()
    return showMessage(project, parentComponent, mac = { window ->
      MacMessages.getInstance().showYesNoDialog(title, message, yesText, noText, window, doNotAskOption, icon, helpId)
    }, other = {
      MessagesService.getInstance().showMessageDialog(project = project, parentComponent = parentComponent,
                                                      message = message, title = title, options = arrayOf(yesText, noText),
                                                      icon = icon,
                                                      doNotAskOption = doNotAskOption, alwaysUseIdeaUI = true) == 0
    })
  }
}

private inline fun <T> showMessage(project: Project?, parentComponent: Component?, mac: (Window?) -> T, other: () -> T): T {
  if (canShowMacSheetPanel() || (SystemInfoRt.isMac && MessagesService.getInstance().isAlertEnabled())) {
    try {
      val window = if (parentComponent == null) {
        WindowManager.getInstance().suggestParentWindow(project)
      }
      else {
        ComponentUtil.getWindow(parentComponent)
      }
      return mac(window)
    }
    catch (e: Exception) {
      if (e.message != "Cannot find any window") {
        logger<MessagesService>().error(e)
      }
    }
  }
  return other()
}

fun canShowMacSheetPanel(): Boolean {
  if (!SystemInfoRt.isMac || getInstance().isAlertEnabled()) {
    return false
  }
  val app = ApplicationManager.getApplication()
  return app != null && !app.isUnitTestMode && !app.isHeadlessEnvironment && Registry.`is`("ide.mac.message.dialogs.as.sheets", true)
}
