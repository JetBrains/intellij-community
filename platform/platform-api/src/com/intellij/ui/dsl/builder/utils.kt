// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.ide.BrowserUtil
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent

/**
 * Component properties for UI DSL
 */
enum class DslComponentProperty {
  /**
   * A mark that component is a label of a row, see [Panel.row]
   *
   * Value: [Boolean]
   */
  ROW_LABEL,

  /**
   * Custom visual paddings, which are used instead of [JComponent.getInsets]
   *
   * Value: [com.intellij.ui.dsl.gridLayout.Gaps]
   */
  VISUAL_PADDINGS,

  /**
   * By default, almost every component have [SpacingConfiguration.verticalComponentGap] above and below it.
   * This flag disables such gap below the component. Should be used in very rare situations (e.g. row with label **and** some additional
   * label-kind components above related to the label component), because most standard cases are covered by Kotlin UI DSL API
   *
   * Value: [Boolean]
   */
  NO_BOTTOM_GAP,

  /**
   * By default, almost every component have [SpacingConfiguration.verticalComponentGap] above and below it.
   * Some components are compound (usually based on [JPanel]) and Kotlin UI DSL doesn't add vertical gaps around it by default.
   * Setting this property forces adding vertical gaps.
   *
   * Value: [Boolean]
   */
  TOP_BOTTOM_GAP,

  /**
   * By default, we're trying to assign [javax.swing.JLabel.setLabelFor] for the cell component itself.
   * In some cases, a wrapper component needs to be used - and this property allows delegating this feature to a child component.
   *
   * Value: [JComponent]
   */
  LABEL_FOR,

  /**
   * Some compound components can contain several components inside itself. [INTERACTIVE_COMPONENT] points to main interactive one,
   * which is assigned to [JLabel.labelFor] and which is used as a component for data validation
   *
   * Value: [JComponent]
   */
  INTERACTIVE_COMPONENT
}

/**
 * Default comment width
 */
const val DEFAULT_COMMENT_WIDTH = 70

/**
 * Text uses word wrap if there is no enough width
 */
const val MAX_LINE_LENGTH_WORD_WRAP = -1

/**
 * Text is not wrapped and uses only html markup like <br>
 */
const val MAX_LINE_LENGTH_NO_WRAP = Int.MAX_VALUE

fun interface HyperlinkEventAction {

  companion object {
    /**
     * Opens URL in a browser
     */
    @JvmField
    val HTML_HYPERLINK_INSTANCE = HyperlinkEventAction { e -> BrowserUtil.browse(e.url) }
  }

  fun hyperlinkActivated(e: HyperlinkEvent)

  fun hyperlinkEntered(e: HyperlinkEvent) {
  }

  fun hyperlinkExited(e: HyperlinkEvent) {
  }
}
