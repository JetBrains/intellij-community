// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.UINumericRange
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.*
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
   * By default, [Cell.label] assigns [javax.swing.JLabel.setLabelFor] for the cell component.
   * It can be turned off via this property, which could be useful when shortcut is processed manually with some specific action
   *
   * Value: [Boolean]
   */
  SKIP_LABEL_FOR_ASSIGNMENT,

  /**
   * Some compound components can contain several components inside itself. [INTERACTIVE_COMPONENT] points to main interactive one
   *
   * * Assigned to [JLabel.labelFor]
   * * Used as the component for data validation
   * * Used as destination for [Cell.onChanged]
   *
   * Value: [JComponent]
   */
  INTERACTIVE_COMPONENT,

  /**
   * Provides custom icons for icon tag <icon src='key'>. Example of usage in plugins with own icons:
   *
   * ```
   *  text("").applyToComponent { // Custom icons cannot be used before ICONS_PROVIDER is set
   *    putClientProperty(DslComponentProperty.ICONS_PROVIDER, classIconProvider(PluginIcons::class.java))
   *    text = "<icon src='PluginIcons.Icon'>"
   *  }
   * ```
   *
   * Can be applied only to JEditorPane-s created by Kotlin UI DSL (like [Row.text], [Row.comment] and others).
   *
   * Value: [IconsProvider]
   *
   * @see classIconProvider
   */
  ICONS_PROVIDER,
}

/**
 * Default comment width
 */
const val DEFAULT_COMMENT_WIDTH: Int = 70

/**
 * Text uses word wrap if there is no enough width
 */
const val MAX_LINE_LENGTH_WORD_WRAP: Int = -1

/**
 * Text is not wrapped and uses only html markup like `<br>`
 */
const val MAX_LINE_LENGTH_NO_WRAP: Int = Int.MAX_VALUE

fun interface HyperlinkEventAction {

  companion object {
    /**
     * Opens URL in a browser
     */
    @JvmField
    val HTML_HYPERLINK_INSTANCE: HyperlinkEventAction = HyperlinkEventAction { e ->
      e.url?.let { BrowserUtil.browse(it) }
    }
  }

  fun hyperlinkActivated(e: HyperlinkEvent)

  fun hyperlinkEntered(e: HyperlinkEvent) {
  }

  fun hyperlinkExited(e: HyperlinkEvent) {
  }
}

fun interface IconsProvider {
  fun getIcon(key: String): Icon?
}

/**
 * Values meaning:
 *
 * null - use default logic for vertical component gap
 * true - force setting [SpacingConfiguration.verticalComponentGap]
 * false - force setting 0 as a vertical gap
 */
data class VerticalComponentGap(val top: Boolean? = null, val bottom: Boolean? = null) {
  companion object {
    @JvmField
    val NONE: VerticalComponentGap = VerticalComponentGap(top = false, bottom = false)

    @JvmField
    val BOTH: VerticalComponentGap = VerticalComponentGap(top = true, bottom = true)
  }
}

fun UINumericRange.asRange(): IntRange = min..max

@Deprecated("Use com.intellij.ui.dsl.listCellRenderer.BuilderKt.textListCellRenderer/listCellRenderer instead", level = DeprecationLevel.ERROR)
@ApiStatus.ScheduledForRemoval
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

fun classIconProvider(iconClass: Class<*>): IconsProvider {
  return IconsProvider {
    if (it.startsWith(iconClass.simpleName + '.')) {
      IconLoader.findIcon(iconClass.packageName + '.' + it, iconClass)
    }
    else null
  }
}
