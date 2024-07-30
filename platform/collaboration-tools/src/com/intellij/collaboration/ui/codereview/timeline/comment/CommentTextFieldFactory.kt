// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.timeline.comment

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min

object CommentTextFieldFactory {

  /**
   * Use [com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentTextFieldFactory] or create a standalone editor
   */
  @Obsolete
  fun create(
    project: Project?,
    document: Document,
    scrollOnChange: ScrollOnChangePolicy = ScrollOnChangePolicy.ScrollToField,
    placeHolder: @Nls String? = null
  ): EditorTextField = CommentTextField(project, document).apply {
    putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    setPlaceholder(placeHolder)
    addSettingsProvider {
      it.putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
      it.colorsScheme.lineSpacing = 1f
      it.settings.isUseSoftWraps = true
      it.isEmbeddedIntoDialogWrapper = true
      it.contentComponent.isOpaque = false
    }
    installScrollIfChangedController(scrollOnChange)
    selectAll()
  }

  private fun EditorTextField.installScrollIfChangedController(policy: ScrollOnChangePolicy) {
    if (policy == ScrollOnChangePolicy.DontScroll) return

    fun scroll() {
      val field = this
      val parent = field.parent as? JComponent
      when (policy) {
        is ScrollOnChangePolicy.ScrollToComponent -> {
          val componentToScroll = policy.component
          parent?.scrollRectToVisible(Rectangle(0, 0, componentToScroll.width, componentToScroll.height))
        }
        ScrollOnChangePolicy.ScrollToField -> {
          parent?.scrollRectToVisible(Rectangle(0, 0, parent.width, parent.height))
        }
        else -> Unit
      }
    }

    addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        scroll()
      }
    })

    // Previous listener doesn't work properly when text field's size is changed because component is not resized at this moment.
    // Without the following listener component will not be scrolled to when newline is inserted.
    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        if (UIUtil.isFocusAncestor(parent)) {
          scroll()
        }
      }
    })
  }

  sealed class ScrollOnChangePolicy {
    object DontScroll : ScrollOnChangePolicy()
    object ScrollToField : ScrollOnChangePolicy()
    class ScrollToComponent(val component: JComponent) : ScrollOnChangePolicy()
  }

  fun wrapWithLeftIcon(config: IconConfig, item: JComponent): JComponent {
    val (icon, iconGap) = config
    val iconLabel = JLabel(icon)
    return JPanel(CommentFieldWithIconLayout(iconGap - CollaborationToolsUIUtil.getFocusBorderInset()) {
      item.takeIf { it.isVisible }?.minimumSize?.height ?: 0
    }).apply {
      isOpaque = false
      add(CommentFieldWithIconLayout.ICON, iconLabel)
      add(CommentFieldWithIconLayout.ITEM, item)
    }
  }

  internal fun wrapWithLeftIcon(config: IconConfig, item: JComponent, minimalItemHeightCalculator: () -> Int): JComponent {
    val (icon, iconGap) = config
    val iconLabel = JLabel(icon)
    return JPanel(CommentFieldWithIconLayout(iconGap - CollaborationToolsUIUtil.getFocusBorderInset(), minimalItemHeightCalculator)).apply {
      isOpaque = false
      add(CommentFieldWithIconLayout.ICON, iconLabel)
      add(CommentFieldWithIconLayout.ITEM, item)
    }
  }

  data class IconConfig(val icon: Icon, val gap: Int) {
    companion object {
      fun <T> of(type: CodeReviewChatItemUIUtil.ComponentType, iconsProvider: IconsProvider<T>, iconKey: T): IconConfig =
        IconConfig(iconsProvider.getIcon(iconKey, type.iconSize), type.iconGap)
    }
  }
}

private class CommentTextField(
  project: Project?,
  document: Document
) : EditorTextField(document, project, FileTypes.PLAIN_TEXT) {
  init {
    isOneLineMode = false
  }

  //always paint pretty border
  override fun updateBorder(editor: EditorEx) = setupBorder(editor)

  override fun createEditor(): EditorEx {
    // otherwise border background is painted from multiple places
    return super.createEditor().apply {
      //TODO: fix in editor
      //com.intellij.openapi.editor.impl.EditorImpl.getComponent() == non-opaque JPanel
      // which uses default panel color
      component.isOpaque = false
      //com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder.paintBorder
      scrollPane.isOpaque = false
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    val editor = editor ?: return
    sink[PlatformCoreDataKeys.FILE_EDITOR] = TextEditorProvider.getInstance().getTextEditor(editor)
  }
}

/**
 * Lays out the field with an icon on the left.
 * Icon is aligned to the top of its column except when min height of the field is less than that of an icon,
 * in this case avatar is centered along that min height.
 * Same thing the other way around.
 */
private class CommentFieldWithIconLayout(
  private val gap: Int,
  private val minimalItemHeightCalculator: () -> Int
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
    val itemMinHeight = minimalItemHeightCalculator()

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