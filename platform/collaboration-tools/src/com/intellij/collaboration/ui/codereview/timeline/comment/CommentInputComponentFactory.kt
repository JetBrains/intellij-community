// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.timeline.comment

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.Nls
import java.awt.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min

object CommentInputComponentFactory {
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