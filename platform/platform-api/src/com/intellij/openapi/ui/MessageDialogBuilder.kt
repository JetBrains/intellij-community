// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.DoNotAskOption
import com.intellij.openapi.ui.Messages.YesNoCancelResult
import com.intellij.openapi.ui.Messages.YesNoResult
import com.intellij.openapi.ui.messages.MessagesService
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.mac.MacMessages
import javax.swing.Icon

abstract class MessageDialogBuilder<T : MessageDialogBuilder<T>> private constructor(protected val title: @NlsContexts.DialogTitle String,
                                                                                     protected val message: @DialogMessage String) {
  protected var yesText: String? = null
  protected var noText: String? = null
  protected var project: Project? = null
  protected var icon: Icon? = null
  protected var doNotAskOption: DoNotAskOption? = null
  protected abstract fun getThis(): T

  companion object {
    @JvmStatic
    fun yesNo(title: @NlsContexts.DialogTitle String, message: @DialogMessage String): YesNo {
      return YesNo(title, message).icon(Messages.getQuestionIcon())
    }

    @JvmStatic
    fun yesNoCancel(title: @NlsContexts.DialogTitle String, message: @DialogMessage String): YesNoCancel {
      return YesNoCancel(title, message).icon(Messages.getQuestionIcon())
    }
  }

  fun project(project: Project?): T {
    this.project = project
    return getThis()
  }

  /**
   * @see Messages.getInformationIcon
   * @see Messages.getWarningIcon
   * @see Messages.getErrorIcon
   * @see Messages.getQuestionIcon
   */
  fun icon(icon: Icon): T {
    this.icon = icon
    return getThis()
  }

  fun doNotAsk(doNotAskOption: DoNotAskOption): T {
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

    val isYes: Boolean
      get() = show() == Messages.YES

    @YesNoResult
    fun show(): Int {
      val yesText = yesText ?: Messages.getYesButton()
      val noText = noText ?: Messages.getNoButton()
      try {
        if (Messages.canShowMacSheetPanel()) {
          val window = WindowManager.getInstance().suggestParentWindow(project)
          return MacMessages.getInstance().showYesNoDialog(title, message, yesText, noText, window, doNotAskOption)
        }
      }
      catch (ignored: Exception) {
      }

      val options = arrayOf(yesText, noText)
      if (MessagesService.getInstance().showMessageDialog(project, null, message, title, options, 0, -1, icon, doNotAskOption, false) == 0) {
        return Messages.YES
      }
      else {
        return Messages.NO
      }
    }
  }

  class YesNoCancel internal constructor(title: String, message: String) : MessageDialogBuilder<YesNoCancel>(title, message) {
    private var cancelText: String? = null

    fun cancelText(cancelText: @NlsContexts.Button String): YesNoCancel {
      this.cancelText = cancelText
      return getThis()
    }

    override fun getThis() = this

    @YesNoCancelResult
    fun show(): Int {
      val yesText = yesText ?: Messages.getYesButton()
      val noText = noText ?: Messages.getNoButton()
      val cancelText = cancelText ?: Messages.getCancelButton()
      try {
        if (Messages.canShowMacSheetPanel()) {
          val window = WindowManager.getInstance().suggestParentWindow(project)
          return MacMessages.getInstance().showYesNoCancelDialog(title, message, yesText, noText, cancelText, window, doNotAskOption)
        }
      }
      catch (ignored: Exception) {
      }

      val options = arrayOf(yesText, noText, cancelText)
      return when (Messages.showDialog(project, message, title, options, 0, icon, doNotAskOption)) {
        0 -> Messages.YES
        1 -> Messages.NO
        else -> Messages.CANCEL
      }
    }
  }
}