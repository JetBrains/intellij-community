// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.timeline.comment

import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory.CancelActionConfig
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory.ScrollOnChangePolicy
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory.SubmitActionConfig
import com.intellij.collaboration.ui.util.JComponentOverlay
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JLabel

object CommentInputComponentFactory {
  val defaultSubmitShortcut: ShortcutSet = CommonShortcuts.CTRL_ENTER
  val defaultCancelShortcut: ShortcutSet = CommonShortcuts.ESCAPE

  fun create(
    model: CommentTextFieldModel,
    textField: EditorTextField,
    config: Config
  ): JComponent {
    textField.installSubmitAction(model, config.submitConfig)
    textField.installCancelAction(config.cancelConfig)

    val busyLabel = JLabel(AnimatedIcon.Default())
    val textFieldWithOverlay = JComponentOverlay.createCentered(textField, busyLabel)
    updateUiOnModelChanges(textFieldWithOverlay, model, textField, busyLabel)
    installScrollIfChangedController(textFieldWithOverlay, model, config.scrollOnChange)

    return textFieldWithOverlay
  }

  fun getEditorTextFieldVerticalOffset() = if (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) 6 else 4

  data class Config(
    val scrollOnChange: ScrollOnChangePolicy = ScrollOnChangePolicy.ScrollToField,
    val submitConfig: SubmitActionConfig = SubmitActionConfig(),
    val cancelConfig: CancelActionConfig? = null
  )

  data class SubmitActionConfig(
    val shortcut: ShortcutSet = defaultSubmitShortcut
  )

  data class CancelActionConfig(
    val shortcut: ShortcutSet = defaultCancelShortcut,
    val action: ActionListener
  )

  sealed class ScrollOnChangePolicy {
    object DontScroll : ScrollOnChangePolicy()
    object ScrollToField : ScrollOnChangePolicy()
    class ScrollToComponent(val component: JComponent) : ScrollOnChangePolicy()
  }
}

private fun installScrollIfChangedController(
  parent: JComponent,
  model: CommentTextFieldModel,
  policy: ScrollOnChangePolicy,
) {
  if (policy == ScrollOnChangePolicy.DontScroll) {
    return
  }
  fun scroll() {
    when (policy) {
      ScrollOnChangePolicy.DontScroll -> {
      }
      is ScrollOnChangePolicy.ScrollToComponent -> {
        val componentToScroll = policy.component
        parent.scrollRectToVisible(Rectangle(0, 0, componentToScroll.width, componentToScroll.height))
      }
      ScrollOnChangePolicy.ScrollToField -> {
        parent.scrollRectToVisible(Rectangle(0, 0, parent.width, parent.height))
      }
    }
  }

  model.document.addDocumentListener(object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      scroll()
    }
  })

  // previous listener doesn't work properly when text field's size is changed because
  // component is not resized at this moment, so we need to handle resizing too
  // it also produces such behavior: resize of the ancestor will scroll to the field
  parent.addComponentListener(object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      if (UIUtil.isFocusAncestor(parent)) {
        scroll()
      }
    }
  })
}

private fun EditorTextField.installSubmitAction(
  model: CommentTextFieldModel,
  submitConfig: SubmitActionConfig
) {
  val submitAction = object : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) = model.submitWithCheck()
  }
  submitAction.registerCustomShortcutSet(submitConfig.shortcut, this)
}

private fun EditorTextField.installCancelAction(cancelConfig: CancelActionConfig?) {
  if (cancelConfig == null) {
    return
  }
  object : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
      cancelConfig.action.actionPerformed(
        ActionEvent(this@installCancelAction, e.inputEvent.id, "cancel", e.inputEvent.`when`, e.inputEvent.modifiersEx)
      )
    }
  }.registerCustomShortcutSet(cancelConfig.shortcut, this)
}

private fun updateUiOnModelChanges(
  parent: JComponent,
  model: CommentTextFieldModel,
  textField: EditorTextField,
  busyLabel: JComponent
) {
  fun update() {
    busyLabel.isVisible = model.isBusy
  }

  textField.addDocumentListener(object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      update()
      parent.revalidate()
    }
  })

  model.addStateListener(::update)
  update()
}

private fun CommentTextFieldModel.isSubmitAllowed(): Boolean = !isBusy && content.text.isNotBlank()

private fun CommentTextFieldModel.submitWithCheck() {
  if (isSubmitAllowed()) {
    submit()
  }
}