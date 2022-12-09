// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview

import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.hover.HoverStateListener
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI.Panels.simplePanel
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.Component
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

  // TODO: custom layouts
  object ComponentFactory {
    fun <T> wrapWithIcon(componentType: ComponentType, item: JComponent,
                         iconProvider: IconsProvider<T>, iconKey: T, iconTooltip: @Nls String? = null): JComponent {
      val iconLabel = JLabel(iconProvider.getIcon(iconKey, componentType.iconSize)).apply {
        toolTipText = iconTooltip
      }
      val iconPanel = simplePanel().addToTop(iconLabel).andTransparent()
      return simplePanel(componentType.iconGap, 0).addToCenter(item).addToLeft(iconPanel).andTransparent()
    }

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