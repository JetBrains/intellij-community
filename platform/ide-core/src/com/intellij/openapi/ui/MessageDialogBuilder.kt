// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.CommonBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageConstants.*
import com.intellij.openapi.ui.messages.MessagesService
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Component
import javax.swing.Icon

sealed class MessageDialogBuilder<T : MessageDialogBuilder<T>>(protected val title: @NlsContexts.DialogTitle String,
                                                               protected val message: @NlsContexts.DialogMessage String) {
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
    fun yesNo(title: @NlsContexts.DialogTitle String, message: @NlsContexts.DialogMessage String): YesNo {
      return YesNo(title, message).icon(UIUtil.getQuestionIcon())
    }

    @JvmStatic
    fun yesNo(title: @NlsContexts.DialogTitle String, message: @NlsContexts.DialogMessage String, icon: Icon?): YesNo {
      return YesNo(title, message).icon(icon)
    }

    @JvmStatic
    fun okCancel(title: @NlsContexts.DialogTitle String, message: @NlsContexts.DialogMessage String): OkCancelDialogBuilder {
      return OkCancelDialogBuilder(title, message).icon(UIUtil.getQuestionIcon())
    }

    @JvmStatic
    fun yesNoCancel(title: @NlsContexts.DialogTitle String, message: @NlsContexts.DialogMessage String): YesNoCancel {
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

    /** Use this method only if you do not know neither a project nor a component. */
    fun guessWindowAndAsk() = show(project = null, parentComponent = null)

    @Deprecated(message = "Use ask(project)", level = DeprecationLevel.ERROR)
    fun isYes(): Boolean = show(project = null, parentComponent = null)

    @Deprecated(message = "Use ask(project)", level = DeprecationLevel.ERROR)
    fun show(): Int = if (show(project = project, parentComponent = parentComponent)) YES else NO

    @YesNoResult
    private fun show(project: Project?, parentComponent: Component?): Boolean {
      val yesText = yesText ?: CommonBundle.getYesButtonText()
      val noText = noText ?: CommonBundle.getNoButtonText()
      return MessagesService.getInstance().showMessageDialog(
        project = project, parentComponent = parentComponent, message = message, title = title, icon = icon,
        options = arrayOf(yesText, noText), doNotAskOption = doNotAskOption, helpId = helpId, alwaysUseIdeaUI = true
      ) == YES
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

    /** Use this method only if you do not know neither a project nor a component. */
    @YesNoCancelResult
    fun guessWindowAndAsk() = show(project = null, parentComponent = null)

    @YesNoCancelResult
    private fun show(project: Project?, parentComponent: Component?): Int {
      val yesText = yesText ?: CommonBundle.getYesButtonText()
      val noText = noText ?: CommonBundle.getNoButtonText()
      val cancelText = cancelText ?: CommonBundle.getCancelButtonText()
      val options = arrayOf(yesText, noText, cancelText)
      val choice = MessagesService.getInstance().showMessageDialog(
        project = project, parentComponent = parentComponent, message = message, title = title, options = options,
        icon = icon, doNotAskOption = doNotAskOption, helpId = helpId, alwaysUseIdeaUI = true)
      return when (choice) {
        0 -> YES
        1 -> NO
        else -> CANCEL
      }
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
      val result = MessagesService.getInstance().showMessageDialog(
        project = project, parentComponent = parentComponent, message = message, title = title, options = options,
        defaultOptionIndex = defaultOptionIndex, focusedOptionIndex = focusedOptionIndex,
        icon = icon, doNotAskOption = doNotAskOption, helpId = helpId, alwaysUseIdeaUI = true)
      return if (result < 0) null else buttons[result]
    }
  }
}

class OkCancelDialogBuilder internal constructor(title: String, message: String) : MessageDialogBuilder<OkCancelDialogBuilder>(title, message) {
  override fun getThis() = this

  fun ask(project: Project?) = show(project = project, parentComponent = null)

  fun ask(parentComponent: Component?) = show(project = null, parentComponent = parentComponent)

  /** Use this method only if you do not know neither a project nor a component. */
  fun guessWindowAndAsk() = show(project = null, parentComponent = null)

  private fun show(project: Project?, parentComponent: Component?): Boolean {
    val yesText = yesText ?: CommonBundle.getOkButtonText()
    val noText = noText ?: CommonBundle.getCancelButtonText()
    return MessagesService.getInstance().showMessageDialog(
      project = project, parentComponent = parentComponent, message = message, title = title, options = arrayOf(yesText, noText),
      icon = icon, doNotAskOption = doNotAskOption, alwaysUseIdeaUI = true) == 0
  }
}
