// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui

import com.intellij.CommonBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.DoNotAskOption
import com.intellij.openapi.ui.Messages.*
import com.intellij.openapi.ui.messages.MessagesService
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ComponentUtil
import com.intellij.ui.mac.MacMessages
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
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
  protected abstract fun getThis(): T

  companion object {
    @JvmStatic
    fun yesNo(title: @NlsContexts.DialogTitle String, message: @DialogMessage String): YesNo {
      return YesNo(title, message).icon(UIUtil.getQuestionIcon())
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

  @Deprecated(message = "Pass parentComponent to show", level = DeprecationLevel.ERROR)
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  fun parentComponent(parentComponent: Component?): T {
    this.parentComponent = parentComponent
    return getThis()
  }

  @Deprecated(message = "Pass project to show", level = DeprecationLevel.ERROR)
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  fun project(project: Project?): T {
    this.project = project
    return getThis()
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
      return showMessage(project, parentComponent, mac = { window ->
        MacMessages.getInstance().showYesNoDialog(title, message, yesText, noText, window, doNotAskOption)
      }, other = {
        (MessagesService.getInstance().showMessageDialog(project = project, parentComponent = parentComponent,
                                                         message = message, title = title, icon = icon,
                                                         options = arrayOf(yesText, noText),
                                                         doNotAskOption = doNotAskOption) == 0)
      })
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

    @Deprecated(message = "Use show(project)", level = DeprecationLevel.ERROR)
    @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
    fun show() = show(project = project, parentComponent = parentComponent)

    @YesNoCancelResult
    private fun show(project: Project?, parentComponent: Component?): Int {
      val yesText = yesText ?: CommonBundle.getYesButtonText()
      val noText = noText ?: CommonBundle.getNoButtonText()
      val cancelText = cancelText ?: CommonBundle.getCancelButtonText()
      return showMessage(project, parentComponent, mac = { window ->
        MacMessages.getInstance().showYesNoCancelDialog(title, message, yesText, noText, cancelText, window, doNotAskOption)
      }, other = {
        val options = arrayOf(yesText, noText, cancelText)
        when (MessagesService.getInstance().showMessageDialog(project = project, parentComponent = parentComponent,
                                                              message = message, title = title, icon = icon,
                                                              options = options,
                                                              doNotAskOption = doNotAskOption)) {
          0 -> YES
          1 -> NO
          else -> CANCEL
        }
      })
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
      MacMessages.getInstance().showYesNoDialog(title, message, yesText, noText, window, doNotAskOption)
    }, other = {
      MessagesService.getInstance().showMessageDialog(project = project, parentComponent = parentComponent,
                                                      message = message, title = title, icon = icon,
                                                      options = arrayOf(yesText, noText),
                                                      doNotAskOption = doNotAskOption) == 0
    })
  }
}

private inline fun <T> showMessage(project: Project?, parentComponent: Component?, mac: (Window?) -> T, other: () -> T): T {
  try {
    if (canShowMacSheetPanel()) {
      val window = if (parentComponent == null) {
        WindowManager.getInstance().suggestParentWindow(project)
      }
      else {
        ComponentUtil.getWindow(parentComponent)
      }
      return mac(window)
    }
  }
  catch (e: Exception) {
    logger<MessagesService>().error(e)
  }
  return other()
}
