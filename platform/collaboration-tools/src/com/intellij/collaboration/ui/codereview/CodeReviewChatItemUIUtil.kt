// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview

import com.intellij.collaboration.ui.CollaborationToolsUIUtil.wrapWithLimitedSize
import com.intellij.collaboration.ui.JPanelWithBackground
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.util.CodeReviewColorUtil
import com.intellij.ui.hover.HoverStateListener
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels.simplePanel
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Insets
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

  // we use unscaled insets, bc they will be scaled when we create the border
  @Suppress("UseDPIAwareInsets")
  enum class ComponentType {
    /**
     * Full-sized component, to be used in timeline
     */
    FULL {
      override val iconSize: Int = Avatar.Sizes.TIMELINE
      override val iconGap: Int = 14
      override val paddingInsets: Insets = Insets(CodeReviewTimelineUIUtil.ITEM_VERT_PADDING,
                                                  CodeReviewTimelineUIUtil.ITEM_HOR_PADDING,
                                                  CodeReviewTimelineUIUtil.ITEM_VERT_PADDING,
                                                  CodeReviewTimelineUIUtil.ITEM_HOR_PADDING)
      override val inputPaddingInsets: Insets = paddingInsets
    },

    /**
     * Special horizontally shifted compact-sized component, to be used in second level of timeline
     */
    FULL_SECONDARY {
      override val iconSize: Int = Avatar.Sizes.BASE
      override val iconGap: Int = 10
      override val paddingInsets: Insets = Insets(4, FULL.fullLeftShift, 4, CodeReviewTimelineUIUtil.ITEM_HOR_PADDING)
      override val inputPaddingInsets: Insets = Insets(6, FULL.fullLeftShift, 6, CodeReviewTimelineUIUtil.ITEM_HOR_PADDING)
    },

    /**
     * Compact-sized component to be used in diffs and other places where space is scarce
     */
    COMPACT {
      override val iconSize: Int = Avatar.Sizes.BASE
      override val iconGap: Int = 10
      override val paddingInsets: Insets = Insets(4, CodeReviewCommentUIUtil.INLAY_PADDING, 4, CodeReviewCommentUIUtil.INLAY_PADDING)
      override val inputPaddingInsets: Insets = Insets(6, CodeReviewCommentUIUtil.INLAY_PADDING, 6, CodeReviewCommentUIUtil.INLAY_PADDING)
    },

    /**
     * Same as [COMPACT] but without any padding at all
     */
    SUPER_COMPACT {
      override val iconSize: Int = Avatar.Sizes.BASE
      override val iconGap: Int = 10
      override val paddingInsets: Insets = Insets(0,0,0,0)
      override val inputPaddingInsets: Insets = Insets(0,0,0,0)
    };

    /**
     * Size of a component icon
     */
    abstract val iconSize: Int

    /**
     * Gap between icon and component body
     */
    abstract val iconGap: Int

    /**
     * Component padding that is included in hover
     */
    abstract val paddingInsets: Insets

    /**
     * Padding for the input component related to the item
     */
    abstract val inputPaddingInsets: Insets

    /**
     * Item body shift from the left side
     */
    val fullLeftShift: Int
      get() = paddingInsets.left + iconSize + iconGap

    /**
     * Item body shift from the left side without padding
     */
    val contentLeftShift: Int
      get() = iconSize + iconGap
  }

  fun build(type: ComponentType,
            iconProvider: (iconSize: Int) -> Icon,
            content: JComponent,
            init: Builder.() -> Unit): JComponent =
    buildDynamic(type, { iconSize -> SingleValueModel(iconProvider(iconSize)) }, content, init)

  fun buildDynamic(type: ComponentType,
                   iconValueProvider: (iconSize: Int) -> SingleValueModel<Icon>,
                   content: JComponent,
                   init: Builder.() -> Unit): JComponent =
    Builder(type, iconValueProvider, content).apply(init).build()

  /**
   * One-time use builder, be careful not to leak [header]
   */
  class Builder(
    private val type: ComponentType,
    private val iconValueProvider: (Int) -> SingleValueModel<Icon>,
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
     * Actions component will only be visible on item hover
     */
    var header: HeaderComponents? = null

    /**
     * Helper fun to setup [HeaderComponents]
     */
    fun withHeader(title: JComponent, actions: JComponent? = null) = apply {
      header = HeaderComponents(title, actions)
    }

    fun build(): JComponent =
      content.wrapIfNotNull(maxContentWidth) { comp, maxWidth ->
        wrapWithLimitedSize(comp, maxWidth = maxWidth)
      }.wrapIfNotNull(header) { comp, (title, actions) ->
        ComponentFactory.wrapWithHeader(comp, title, actions)
      }.let {
        val iconLabel = JLabel().apply {
          toolTipText = iconTooltip
          border = JBUI.Borders.emptyRight(type.iconGap)
          iconValueProvider(type.iconSize).addAndInvokeListener { newIcon ->
            icon = newIcon
          }
        }
        val iconPanel = simplePanel().addToTop(iconLabel).andTransparent()
        simplePanel(it).addToLeft(iconPanel).andTransparent()
      }.also {
        actionsVisibleOnHover(it, header?.actions)
      }.apply {
        border = JBUI.Borders.empty(type.paddingInsets)
      }.let { withHoverHighlight(it) }

    private fun <T> JComponent.wrapIfNotNull(value: T?, block: (JComponent, T) -> JComponent): JComponent = let {
      if (value != null) block(it, value) else it
    }
  }

  data class HeaderComponents(val title: JComponent, val actions: JComponent?)

  // TODO: custom layouts
  object ComponentFactory {
    fun wrapWithHeader(item: JComponent, title: JComponent, actions: JComponent?): JComponent {
      val headerPanel = JPanel(null).apply {
        layout = MigLayout(LC().gridGap("0", "0").insets("0").height("16")
                             .hideMode(3).fill())
        isOpaque = false

        add(title, CC().push())
        if (actions != null) {
          add(actions, CC().push().gapLeft("10:push"))
        }
      }

      return VerticalListPanel(4).apply {
        add(headerPanel)
        add(item)
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

  fun withHoverHighlight(comp: JComponent): JComponent {
    val highlighterPanel = JPanelWithBackground(BorderLayout()).apply {
      isOpaque = false
      background = null
      add(comp, BorderLayout.CENTER)
    }.also {
      object : HoverStateListener() {
        override fun hoverChanged(component: Component, hovered: Boolean) {
          // TODO: extract to theme colors
          component.background = if (hovered) {
            CodeReviewColorUtil.Review.Chat.hover
          }
          else {
            null
          }
        }
      }.apply {
        // reset hover to false
        mouseExited(it)
      }.addTo(it)
    }
    return highlighterPanel
  }
}