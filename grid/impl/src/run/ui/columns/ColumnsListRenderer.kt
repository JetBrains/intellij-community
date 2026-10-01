package com.intellij.database.run.ui.columns

import com.intellij.database.DataGridBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.paint.RectanglePainter
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder

/**
 * The shape of one row.
 *
 * The renderer draws these and [ColumnsListPopup] hit tests against them, so both read them from here.
 */
internal const val CONTROLS_LEFT_PAD: Int = 28
internal const val CONTROLS_RIGHT_PAD: Int = 10
internal const val CONTROL_GAP: Int = 10

/** The spacer row of the pinned group line. The line takes its middle, and the rest is padding. */
internal const val SEPARATOR_HEIGHT: Int = 7
internal const val SEPARATOR_LINE_HEIGHT: Int = 1

internal val checkBoxWidth: Int get() = JCheckBox().preferredSize.width
internal val pinWidth: Int get() = AllIcons.General.Pin.iconWidth
internal val gripWidth: Int get() = AllIcons.General.Drag.iconWidth

private val disabledPin: Icon get() = IconLoader.getDisabledIcon(AllIcons.General.Pin)

/** How far a row of a nested column tree sits from the left edge. */
internal fun indent(item: ColumnsListItem): Int = (UIUtil.getTreeLeftChildIndent() + UIUtil.getTreeRightChildIndent()) * item.depth

/** A label that keeps the room of [icon] even while it shows none, plus [trailingGap] of space after it. */
private fun iconLabel(icon: Icon, trailingGap: Int): JLabel = JLabel().apply {
  horizontalAlignment = SwingConstants.LEFT
  preferredSize = Dimension(icon.iconWidth + JBUIScale.scale(trailingGap), icon.iconHeight)
}

/** Draws a row of the column list. [ColumnsListPopup] owns what a row means, and this owns how it looks. */
internal class ItemRenderer(
  private val pinningEnabled: Boolean,
  private val reorderable: Boolean,
) : ListCellRenderer<ColumnsListPopup.Row> {
  /** The row under the pointer, or -1. The popup keeps this, because the list paints no hover of its own. */
  var hoveredRow: Int = -1

  private val checkBox = JCheckBox().apply {
    isOpaque = false
    // The row reads as one thing, so a screen reader must not stop on an unnamed check box of its own.
    isFocusable = false
    accessibleContext.accessibleName = ""
  }
  private val groupIcon = JLabel(AllIcons.Json.Object).apply {
    isOpaque = false
    horizontalAlignment = SwingConstants.LEFT
  }
  private val checkBoxHolder = BorderLayoutPanel().apply {
    isOpaque = false
    addToLeft(groupIcon)
    addToCenter(checkBox)
  }
  private val text = SimpleColoredComponent().apply { isOpaque = false }
  private val pin = iconLabel(AllIcons.General.Pin, CONTROL_GAP)

  /** Says that the hovered row can be dragged. The drag itself starts anywhere on the row. */
  private val grip = iconLabel(AllIcons.General.Drag, 0)

  /** Both controls keep their room while they show no icon, so the name does not reflow on hover. */
  private val controls = BorderLayoutPanel().apply {
    isOpaque = false
    border = JBUI.Borders.empty(0, CONTROLS_LEFT_PAD, 0, CONTROLS_RIGHT_PAD)
    addToCenter(pin)
    addToRight(grip)
  }

  /** The fill behind the row, or null while the row is neither selected nor under the pointer. */
  private var rowBackground: Color? = null

  /** The corner radius comes from `Popup.Selection.arc`, so an island theme rounds this without a second rule. */
  private val content = object : BorderLayoutPanel() {
    override fun paintComponent(g: Graphics) {
      val background = rowBackground ?: return
      g.color = background
      RectanglePainter.FILL.paint(g as Graphics2D, 0, 0, width, height, JBUI.CurrentTheme.Popup.Selection.ARC.get())
    }
  }.apply {
    isOpaque = false
    border = JBUI.Borders.empty(1, 0)
    addToLeft(checkBoxHolder)
    addToCenter(text)
    addToRight(controls)
  }

  /** Holds the pinned group line, which the list paints, so the row carries no hover and no selection. */
  private val separator = JPanel().apply {
    isOpaque = false
    accessibleContext.accessibleName = DataGridBundle.message("action.Console.TableResult.ColumnsList.Popup.PinnedGroupEnd")
    preferredSize = Dimension(0, JBUIScale.scale(SEPARATOR_HEIGHT))
    // WideSelectionListUI raises every row to the platform row height unless a row opts out here.
    putClientProperty(JBList.IGNORE_LIST_ROW_HEIGHT, true)
  }

  override fun getListCellRendererComponent(
    list: JList<out ColumnsListPopup.Row>,
    value: ColumnsListPopup.Row,
    index: Int,
    selected: Boolean,
    focused: Boolean,
  ): Component {
    if (value is ColumnsListPopup.Row.Separator) return separator
    return renderItem(list, (value as ColumnsListPopup.Row.Item).value, index, selected)
  }

  private fun renderItem(list: JList<out ColumnsListPopup.Row>, value: ColumnsListItem, index: Int, selected: Boolean): Component {
    checkBox.isVisible = value.isColumn
    groupIcon.isVisible = !value.isColumn
    checkBox.isSelected = value.visible
    // The tree indent already includes the UI scale.
    @Suppress("UseDPIAwareBorders")
    val border = EmptyBorder(0, indent(value), 0, 0)
    checkBox.border = border
    groupIcon.border = border
    groupIcon.preferredSize = checkBox.preferredSize
    checkBoxHolder.preferredSize = checkBox.preferredSize
    text.clear()
    text.icon = value.icon
    text.append(value.name, if (value.visible) SimpleTextAttributes.REGULAR_ATTRIBUTES else SimpleTextAttributes.GRAYED_ATTRIBUTES)
    value.typeText?.let { text.append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
    val hovered = index == hoveredRow
    pin.icon = when {
      !pinningEnabled || !value.isColumn -> null
      !value.pinned && !hovered -> null
      value.canTogglePin -> AllIcons.General.Pin
      else -> disabledPin
    }
    grip.icon = if (reorderable && value.isColumn && hovered) AllIcons.General.Drag else null
    rowBackground = when {
      // Read from the theme, because the list reports a transparent selection to its own UI.
      selected -> JBUI.CurrentTheme.List.Selection.background(list.hasFocus())
      hovered -> RenderingUtil.getHoverBackground(list)
      else -> null
    }
    text.foreground = if (selected) RenderingUtil.getSelectionForeground(list) else RenderingUtil.getForeground(list)
    content.accessibleContext.accessibleName = accessibleName(value)
    return content
  }

  /** The row name, with visibility and pin state for a column. */
  private fun accessibleName(value: ColumnsListItem): @Nls String {
    val name = if (value.typeText == null) value.name else "${value.name}, ${value.typeText}"
    if (!value.isColumn) return name
    val shown = DataGridBundle.message(
      if (value.visible) "action.Console.TableResult.ColumnsList.Popup.RowShown"
      else "action.Console.TableResult.ColumnsList.Popup.RowHidden",
      name
    )
    return if (value.pinned) DataGridBundle.message("action.Console.TableResult.ColumnsList.Popup.RowPinned", shown) else shown
  }
}
