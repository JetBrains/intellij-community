// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.timeline.comment

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory.CancelActionConfig
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory.ScrollOnChangePolicy
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentInputComponentFactory.SubmitActionConfig
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.JComponentOverlay
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorTextField
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min

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

  fun <T> addIconLeft(componentType: CodeReviewChatItemUIUtil.ComponentType, item: JComponent,
                      iconProvider: IconsProvider<T>, iconKey: T, iconTooltip: @Nls String? = null): JComponent {
    val iconLabel = JLabel(iconProvider.getIcon(iconKey, componentType.iconSize)).apply {
      toolTipText = iconTooltip
    }

    return JPanel(CommentFieldWithIconLayout(componentType.iconGap - CollaborationToolsUIUtil.getFocusBorderInset())).apply {
      isOpaque = false
      add(CommentFieldWithIconLayout.ICON, iconLabel)
      add(CommentFieldWithIconLayout.ITEM, item)
    }
  }

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

/**
 * Lays out the field with an icon on the left.
 * Icon is aligned to the top of its column except when min height of the field is less than that of an icon,
 * in this case avatar is centered along that min height.
 * Same thing the other way around.
 */
private class CommentFieldWithIconLayout(
  private val gap: Int
) : LayoutManager {

  companion object {
    const val ICON = "ICON"
    const val ITEM = "ITEM"
  }

  private var iconComponent: Component? = null
  private var itemComponent: Component? = null

  override fun addLayoutComponent(name: String, comp: Component?) {
    when (name) {
      ICON -> iconComponent = comp
      ITEM -> itemComponent = comp
      else -> error("Incorrect name $name")
    }
  }

  override fun removeLayoutComponent(comp: Component) {
    if (iconComponent == comp) iconComponent = null
    if (itemComponent == comp) itemComponent = null
  }

  override fun preferredLayoutSize(parent: Container): Dimension = getSize(parent, Component::getPreferredSize)
  override fun minimumLayoutSize(parent: Container): Dimension = getSize(parent, Component::getMinimumSize)

  private fun getSize(parent: Container, sizeGetter: (Component) -> Dimension?): Dimension {
    val iconSize = iconComponent?.takeIf { it.isVisible }?.let(sizeGetter) ?: Dimension(0, 0)
    val itemSize = itemComponent?.takeIf { it.isVisible }?.let(sizeGetter) ?: Dimension(0, 0)

    val gap = JBUIScale.scale(gap)
    val i = parent.insets

    return Dimension(i.left + iconSize.width + gap + itemSize.width + i.right,
                     i.top + max(iconSize.height, itemSize.height) + i.bottom)
  }

  override fun layoutContainer(parent: Container) {
    val bounds = Rectangle(Point(0, 0), parent.size)
    JBInsets.removeFrom(bounds, parent.insets)
    var x = bounds.x
    val y = bounds.y
    var contentWidth = bounds.width
    val contentHeight = bounds.height

    val iconHeight = iconComponent?.takeIf { it.isVisible }?.preferredSize?.height ?: 0
    val itemMinHeight = itemComponent?.takeIf { it.isVisible }?.minimumSize?.height ?: 0

    iconComponent?.takeIf { it.isVisible }?.apply {
      val prefSize = preferredSize
      val width = min(contentWidth, prefSize.width)
      setBounds(x, y + max(0, (itemMinHeight - iconHeight) / 2), width, min(contentHeight, prefSize.height))
      x += prefSize.width
      x += JBUIScale.scale(gap)

      contentWidth -= width
      contentWidth -= JBUIScale.scale(gap)
    }

    itemComponent?.takeIf { it.isVisible }?.apply {
      val maxSize = maximumSize
      val minSize = minimumSize

      val width = if (contentWidth >= maxSize.width) {
        maxSize.width
      }
      else {
        if (contentWidth >= minSize.width) {
          contentWidth
        }
        else {
          minSize.width
        }
      }

      val height = if (contentHeight >= maxSize.height) {
        maxSize.height
      }
      else {
        if (contentHeight >= minSize.height) {
          contentHeight
        }
        else {
          minSize.height
        }
      }

      setBounds(x, y + max(0, (iconHeight - itemMinHeight) / 2), width, height)
    }
  }
}