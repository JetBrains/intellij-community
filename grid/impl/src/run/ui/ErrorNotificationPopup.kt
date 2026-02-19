package com.intellij.database.run.ui

import com.intellij.database.DataGridBundle
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBDimension
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.BoxLayout
import javax.swing.JPanel

class ErrorNotificationPopup(
  @Nls val dialogTitle: String,
  val throwable: Throwable?,
  val errorText: String?,
): DialogWrapper(null) {
  private val message by lazy { evaluateMessage() }

  private val copyAction: DialogWrapperAction = object : DialogWrapperAction(DataGridBundle.message("button.copy")) {
    override fun doAction(e: ActionEvent?) {
      CopyPasteManager.copyTextToClipboard(message)
    }
  }

  private val copyAndCloseAction: DialogWrapperAction = object : DialogWrapperAction(DataGridBundle.message("dialog.button.copy.and.close")) {
    override fun doAction(e: ActionEvent?) {
      CopyPasteManager.copyTextToClipboard(message)
      close(OK_EXIT_CODE)
    }
  }

  init {
    init()
    isResizable = true
    isModal = true

    title = dialogTitle
    getButton(okAction)?.apply {
      setText(DataGridBundle.message("action.close.text"))
      rootPane.defaultButton = this
    }
  }

  override fun createCenterPanel(): JPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    val textArea = JBTextArea(message).apply {
      isEditable = false
      isFocusable = true
    }
    add(JBScrollPane(textArea))
    preferredSize = JBDimension(750, 400)
  }

  override fun createActions(): Array<out Action?> = arrayOf(copyAction, copyAndCloseAction, okAction)

  private fun evaluateMessage(): String {
    if (throwable == null) return errorText ?: ""
    val sb = StringBuilder()
    var currentThrowable: Throwable? = throwable
    while (currentThrowable != null) {
      sb.append(currentThrowable.message)
      currentThrowable.stackTrace.forEach { line ->
        sb.append('\n')
          .append('\t')
          .append(line)
      }
      sb.append('\n')
      if (currentThrowable == currentThrowable.cause) break
      currentThrowable = currentThrowable.cause
    }
    return sb.toString()
  }

}