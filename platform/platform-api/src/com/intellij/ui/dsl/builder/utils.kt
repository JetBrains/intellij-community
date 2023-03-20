// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.UINumericRange
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
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
   * Value: [UnscaledGaps] or [Gaps]
   */
  VISUAL_PADDINGS,

  /**
   * By default, every component except JPanel have [SpacingConfiguration.verticalComponentGap] above and below it.
   * This behavior can be overridden by this property. Should be used in very rare situations, because most standard cases are covered
   * by Kotlin UI DSL API. It could be needed for example in the following cases:
   * * A component with a label and some additional label-kind components above
   * * Some components are compound and based on [JPanel] that requires [SpacingConfiguration.verticalComponentGap] above and below
   *
   * Value: [VerticalComponentGap]
   */
  VERTICAL_COMPONENT_GAP,

  /**
   * By default, we're trying to assign [javax.swing.JLabel.setLabelFor] for the cell component itself.
   * In some cases, a wrapper component needs to be used - and this property allows delegating this feature to a child component.
   *
   * Value: [JComponent]
   */
  // todo replace usage by INTERACTIVE_COMPONENT and deprecate
  LABEL_FOR,

  /**
   * Some compound components can contain several components inside itself. [INTERACTIVE_COMPONENT] points to main interactive one
   *
   * * Assigned to [JLabel.labelFor]
   * * Used as the component for data validation
   * * Used as destination for [Cell.onChanged]
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
    val HTML_HYPERLINK_INSTANCE = HyperlinkEventAction { e ->
      e.url?.let { BrowserUtil.browse(it) }
    }
  }

  fun hyperlinkActivated(e: HyperlinkEvent)

  fun hyperlinkEntered(e: HyperlinkEvent) {
  }

  fun hyperlinkExited(e: HyperlinkEvent) {
  }
}

/**
 * Values meaning:
 *
 * null - use default logic for vertical component gap
 * true - force setting [SpacingConfiguration.verticalComponentGap]
 * false - force setting 0 as a vertical gap
 */
data class VerticalComponentGap(val top: Boolean? = null, val bottom: Boolean? = null)

fun UINumericRange.asRange(): IntRange = min..max

fun <T> listCellRenderer(renderer: SimpleListCellRenderer<T>.(T) -> Unit): SimpleListCellRenderer<T> {
  return object : SimpleListCellRenderer<T>() {
    override fun customize(list: JList<out T>, value: T, index: Int, selected: Boolean, hasFocus: Boolean) {
      // BasicComboBoxUI.getBaseline can try to get renderer for null value even when comboBox doesn't allow nullable elements
      if (index != -1 || value != null) {
        renderer(value)
      }
    }
  }
}

/**
 * Kotlin UI DSL doesn't allow to use some tags like <html> so all resource strings should be cleared up manually. Sometimes strings are
 * received from outside, in such cases this method can be useful
 *
 * Example: `cleanupHtml("<html>Some string</html>")` returns `"Some string"`
 */
fun cleanupHtml(@Nls s: String): @Nls String {
  val regex = Regex("\\s*<html>(?<body>.*)</html>\\s*", RegexOption.IGNORE_CASE)
  val result = regex.matchEntire(s)
  if (result == null) {
    return s
  }
  @Suppress("HardCodedStringLiteral")
  return result.groups["body"]!!.value
}
