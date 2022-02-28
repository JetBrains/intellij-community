// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.timeline.comment

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.InlineIconButton
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory.CancelActionConfig
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory.ScrollOnChangePolicy
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory.SubmitActionConfig
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory.getEditorTextFieldVerticalOffset
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.UIUtil
import icons.CollaborationToolsIcons
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

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

    val contentComponent = JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(
        LC()
          .gridGap("0", "0")
          .insets("0", "0", "0", "0")
          .fillX()
      )
      border = JBUI.Borders.empty()
    }

    val busyLabel = JLabel(AnimatedIcon.Default())
    val submitButton = createSubmitButton(model, config.submitConfig)

    val cancelButton = createCancelButton(config.cancelConfig)

    updateUiOnModelChanges(contentComponent, model, textField, busyLabel, submitButton)

    installScrollIfChangedController(contentComponent, model, config.scrollOnChange)

    val textFieldWithOverlay = createTextFieldWithOverlay(textField, submitButton, busyLabel)
    contentComponent.add(textFieldWithOverlay, CC().grow().pushX())
    cancelButton?.let { contentComponent.add(it, CC().alignY("top")) }

    return contentComponent
  }

  fun getEditorTextFieldVerticalOffset() = if (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) 6 else 4

  data class Config(
    val scrollOnChange: ScrollOnChangePolicy = ScrollOnChangePolicy.ScrollToField,
    val submitConfig: SubmitActionConfig,
    val cancelConfig: CancelActionConfig?
  )

  data class SubmitActionConfig(
    val iconConfig: ActionButtonConfig?,
    val shortcut: SingleValueModel<ShortcutSet> = SingleValueModel(defaultSubmitShortcut)
  )

  data class CancelActionConfig(
    val iconConfig: ActionButtonConfig?,
    val shortcut: ShortcutSet = defaultCancelShortcut,
    val action: () -> Unit
  )

  sealed class ScrollOnChangePolicy {
    object DontScroll : ScrollOnChangePolicy()
    object ScrollToField : ScrollOnChangePolicy()
    class ScrollToComponent(val component: JComponent) : ScrollOnChangePolicy()
  }

  data class ActionButtonConfig(val name: @NlsContexts.Tooltip String)
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

@Suppress("DialogTitleCapitalization")
private fun createSubmitButton(
  model: CommentTextFieldModel,
  actionConfig: SubmitActionConfig
): InlineIconButton? {
  val iconConfig = actionConfig.iconConfig ?: return null

  val button = InlineIconButton(
    CollaborationToolsIcons.Send, CollaborationToolsIcons.SendHovered,
    tooltip = iconConfig.name
  ).apply {
    putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    actionListener = ActionListener { model.submitWithCheck() }
  }

  actionConfig.shortcut.addAndInvokeListener {
    button.shortcut = it
  }
  return button
}

private fun createCancelButton(actionConfig: CancelActionConfig?): InlineIconButton? {
  if (actionConfig == null) {
    return null
  }
  val iconConfig = actionConfig.iconConfig ?: return null
  return InlineIconButton(
    AllIcons.Actions.Close, AllIcons.Actions.CloseHovered,
    tooltip = iconConfig.name,
    shortcut = actionConfig.shortcut
  ).apply {
    border = JBUI.Borders.empty(getEditorTextFieldVerticalOffset(), 0)
    putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    actionListener = ActionListener { actionConfig.action() }
  }
}

private fun EditorTextField.installSubmitAction(
  model: CommentTextFieldModel,
  submitConfig: SubmitActionConfig
) {
  val submitAction = object : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) = model.submitWithCheck()
  }

  submitConfig.shortcut.addAndInvokeListener {
    submitAction.unregisterCustomShortcutSet(this)
    submitAction.registerCustomShortcutSet(it, this)
  }
}

private fun EditorTextField.installCancelAction(cancelConfig: CancelActionConfig?) {
  if (cancelConfig == null) {
    return
  }
  object : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
      cancelConfig.action()
    }
  }.registerCustomShortcutSet(cancelConfig.shortcut, this)
}

private fun updateUiOnModelChanges(
  parent: JComponent,
  model: CommentTextFieldModel,
  textField: EditorTextField,
  busyLabel: JComponent,
  submitButton: InlineIconButton?
) {
  fun update() {
    busyLabel.isVisible = model.isBusy
    submitButton?.isEnabled = model.isSubmitAllowed()
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

/**
 * Returns a component with [busyLabel] in center and [button] in bottom-right corner
 */
private fun createTextFieldWithOverlay(textField: EditorTextField, button: JComponent?, busyLabel: JComponent?): JComponent {
  if (button != null) {
    val bordersListener = object : ComponentAdapter(), HierarchyListener {
      override fun componentResized(e: ComponentEvent?) {
        val scrollPane = (textField.editor as? EditorEx)?.scrollPane ?: return
        val buttonSize = button.size
        JBInsets.removeFrom(buttonSize, button.insets)
        scrollPane.viewportBorder = JBUI.Borders.emptyRight(buttonSize.width)
        scrollPane.viewport.revalidate()
      }

      override fun hierarchyChanged(e: HierarchyEvent?) {
        val scrollPane = (textField.editor as? EditorEx)?.scrollPane ?: return
        button.border = EmptyBorder(scrollPane.border.getBorderInsets(scrollPane))
        componentResized(null)
      }
    }

    textField.addHierarchyListener(bordersListener)
    button.addComponentListener(bordersListener)
  }

  val layeredPane = object : JLayeredPane() {
    override fun getPreferredSize(): Dimension {
      return textField.preferredSize
    }

    override fun doLayout() {
      super.doLayout()
      textField.setBounds(0, 0, width, height)
      if (button != null) {
        val preferredButtonSize = button.preferredSize
        button.setBounds(width - preferredButtonSize.width, height - preferredButtonSize.height,
                         preferredButtonSize.width, preferredButtonSize.height)
      }
      if (busyLabel != null) {
        busyLabel.bounds = SingleComponentCenteringLayout.getBoundsForCentered(textField, busyLabel)
      }
    }
  }
  layeredPane.add(textField, JLayeredPane.DEFAULT_LAYER, 0)
  var index = 1
  if (busyLabel != null) {
    layeredPane.add(busyLabel, JLayeredPane.POPUP_LAYER, index)
    index++
  }
  if (button != null) {
    layeredPane.add(button, JLayeredPane.POPUP_LAYER, index)
  }

  return layeredPane
}