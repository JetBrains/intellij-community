// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview

import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.hover.HoverStateListener
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels.simplePanel
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.roundToInt

object CodeReviewChatItemUIUtil {

  /**
   * Maximum width for textual content for it to be readable
   * Equals to 42em
   */
  val TEXT_CONTENT_WIDTH: Int
    get() = (JBUIScale.DEF_SYSTEM_FONT_SIZE * 42).roundToInt()

  enum class ComponentType {
    FULL {
      override val iconSize: Int = 30
      override val iconGap: Int = 14
    },
    COMPACT {
      override val iconSize: Int = 20
      override val iconGap: Int = 10
    };

    abstract val iconSize: Int
    abstract val iconGap: Int
  }

  fun build(type: ComponentType, iconProvider: (Int) -> Icon, content: JComponent, init: Builder.() -> Unit): JComponent =
    Builder(type, iconProvider, content).apply(init).build()

  /**
   * One-time use builder, be careful not to leak [header]
   */
  class Builder(
    private val type: ComponentType,
    private val iconProvider: (Int) -> Icon,
    private val content: JComponent
  ) {
    /**
     * Tooltip for a main icon
     */
    var iconTooltip: @Nls String? = null

    /**
     * Content width limit
     */
    var maxContentWidth: Int? = TEXT_CONTENT_WIDTH

    /**
     * Header components - title and actions
     */
    var header: Pair<JComponent, JComponent?>? = null

    fun build(): JComponent =
      content.wrapIfNotNull(maxContentWidth) { comp, maxWidth ->
        ComponentFactory.wrapWithWidthLimit(comp, maxWidth)
      }.wrapIfNotNull(header) { comp, (title, actions) ->
        ComponentFactory.wrapWithHeader(comp, title, actions)
      }.let {
        val iconLabel = JLabel(iconProvider(type.iconSize)).apply {
          toolTipText = iconTooltip
          border = JBUI.Borders.emptyRight(type.iconGap)
        }
        val iconPanel = simplePanel().addToTop(iconLabel).andTransparent()
        simplePanel(it).addToLeft(iconPanel).andTransparent()
      }.also {
        actionsVisibleOnHover(it, header?.second)
      }

    private fun <T> JComponent.wrapIfNotNull(value: T?, block: (JComponent, T) -> JComponent): JComponent = let {
      if (value != null) block(it, value) else it
    }
  }

  // TODO: custom layouts
  object ComponentFactory {
    fun wrapWithHeader(item: JComponent, title: JComponent, actions: JComponent?): JComponent {
      val headerPanel = JPanel(null).apply {
        layout = MigLayout(LC().gridGap("0", "0").insets("0")
                             .hideMode(3).fill())
        isOpaque = false

        add(title, CC().push())
        if (actions != null) {
          add(actions, CC().push().gapLeft("10:push"))
        }
      }

      return JPanel(VerticalLayout(4)).apply {
        isOpaque = false

        add(headerPanel)
        add(item)
      }
    }

    fun wrapWithWidthLimit(item: JComponent, maxWidth: Int): JComponent {
      return JPanel(null).apply {
        layout = MigLayout(LC().gridGap("0", "0").insets("0", "0", "0", "0").fill()).apply {
          columnConstraints = "[][]"
        }
        isOpaque = false
        add(item, CC().grow().push().minWidth("0").maxWidth("${maxWidth}"))
      }
    }
  }

  fun actionsVisibleOnHover(comp: JComponent, actionsPanel: JComponent?) {
    if (actionsPanel != null) {
      object : HoverStateListener() {
        override fun hoverChanged(component: Component, hovered: Boolean) {
          actionsPanel.isVisible = hovered
        }
      }.apply {
        // reset hover to false
        mouseExited(comp)
      }.addTo(comp)
    }
  }
}