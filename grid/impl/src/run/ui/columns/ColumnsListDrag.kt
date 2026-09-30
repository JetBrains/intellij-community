package com.intellij.database.run.ui.columns

import com.intellij.ide.dnd.DnDDragStartBean
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDImage
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.SmoothAutoScroller
import com.intellij.ui.ColorUtil
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.Graphics
import java.awt.Point
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.TransferHandler

private const val DROP_LINE_HEIGHT = 2
private const val DRAG_IMAGE_ALPHA = 0.7f

/** How much of the single-row drag alpha the band keeps. */
private const val BAND_ALPHA_SCALE = 0.5

/**
 * Reorders the rows of the column list by drag.
 *
 * [ColumnsListPopup] owns what a row is and whether a move is allowed. This owns the gesture: the
 * payload, the image, the drop target and the feedback the list paints.
 */
internal class ColumnsListDrag(
  private val popup: ColumnsListPopup,
  private val list: JBList<ColumnsListPopup.Row>,
  private val listModel: CollectionListModel<ColumnsListPopup.Row>,
  private val renderer: ItemRenderer,
) {
  /** The row the drag carries, or -1 while no drag is over the list. It lights the band it may land in. */
  private var draggedRow = -1

  private var dropLine: ColumnsListPopup.DropLine? = null

  /**
   * Installs the drag, rather than use [com.intellij.ui.RowsDnDSupport], which sets no image provider,
   * so nothing would follow the cursor.
   *
   * The whole row is the drag source. The grip only says that the hovered row can be dragged.
   */
  fun install() {
    // RowsDnDSupport installs these two before it builds. Without them the drop target never draws.
    list.transferHandler = TransferHandler(null)
    SmoothAutoScroller.installDropTargetAsNecessary(list)
    DnDSupport.createBuilder(list)
      .setBeanProvider { info -> dragSource(info.point)?.let(popup::dragPayload)?.let { DnDDragStartBean(it, info.point) } }
      .setImageProvider { info -> dragSource(info.point)?.let { dragImage(it, info.point) } }
      .setTargetChecker { event -> checkDrop(event) }
      .setDropHandler { event -> dropRow(event) }
      .setCleanUpOnLeaveCallback { showDropLine(null) }
      .setDropEndedCallback { endDrag() }
      .setDisposableParent(popup.popup)
      .install()
  }

  /** The row a drag may start from, which is any draggable row under [point]. */
  private fun dragSource(point: Point): Int? {
    val row = list.locationToIndex(point)
    if (row < 0 || list.getCellBounds(row, row)?.contains(point) != true) return null
    return if (popup.canMoveRow(row, row)) row else null
  }

  /**
   * The dragged row, built the way `DnDAwareTree.createDragImage` builds one. It covers the whole cell
   * and not the preferred size of the renderer, because a list cell is as wide as the list.
   *
   * `DragGestureEvent.startDrag` measures the offset from the cursor to the image origin, so the negated
   * grab point keeps the grabbed pixel under the cursor.
   */
  private fun dragImage(row: Int, origin: Point): DnDImage? {
    val bounds = list.getCellBounds(row, row) ?: return null
    val component = renderer.getListCellRendererComponent(list, listModel.getElementAt(row), row, false, false)
    (component as? JComponent)?.isOpaque = true
    component.foreground = RenderingUtil.getForeground(list)
    component.background = RenderingUtil.getBackground(list)
    component.font = list.font
    component.setSize(bounds.width, bounds.height)
    val image = UIUtil.createImage(component, bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DRAG_IMAGE_ALPHA)
      component.paint(g)
    }
    finally {
      g.dispose()
    }
    return DnDImage(image, Point(bounds.x - origin.x, bounds.y - origin.y))
  }

  /**
   * Reports whether the drop can land, the way `ServiceViewDragHelper` does: refuse by default, accept
   * with a highlight, and say nothing more when the answer is no.
   *
   * The cursor is not ours. `DnDEvent.setCursor` has no caller anywhere in the monorepo, and the manager
   * derives the cursor from [DnDEvent.setDropPossible] alone. That is why the refusal shows through the
   * highlight: the landing line, and the band of rows the drag may land in.
   */
  @Suppress("SameReturnValue")
  private fun checkDrop(event: DnDEvent): Boolean {
    val from = popup.rowOfPayload(event.attachedObject)
    if (from == null) {
      refuseDrop(event)
      return true
    }
    startDrag(from)
    val to = list.locationToIndex(event.point)
    if (to < 0) {
      refuseDrop(event)
      return true
    }
    val line = lineAt(from, to)
    if (to != from && line == null) {
      refuseDrop(event)
      return true
    }
    event.isDropPossible = true
    showDropLine(line)
    return true
  }

  private fun refuseDrop(event: DnDEvent) {
    event.isDropPossible = false
    showDropLine(null)
  }

  private fun dropRow(event: DnDEvent) {
    val from = popup.rowOfPayload(event.attachedObject)
    val to = list.locationToIndex(event.point)
    showDropLine(null)
    if (from == null || to < 0) return
    // The list may have rebuilt since the grab, so the move is checked again against the rows it has now.
    popup.moveRow(from, to)
  }

  private fun startDrag(row: Int) {
    if (draggedRow == row) return
    draggedRow = row
    list.repaint()
  }

  private fun endDrag() {
    showDropLine(null)
    if (draggedRow < 0) return
    draggedRow = -1
    list.repaint()
  }

  private fun showDropLine(line: ColumnsListPopup.DropLine?) {
    if (dropLine == line) return
    dropLine = line
    list.repaint()
  }

  /**
   * The rows a drag from [row] may land in, which are the rows of the group that [row] belongs to.
   * Empty for no row, for the separator, and for a row that cannot move.
   */
  fun bandRows(row: Int): List<Int> {
    if (row < 0) return emptyList()
    val pinned = popup.itemAt(row)?.pinned ?: return emptyList()
    return (0 until listModel.size).filter { candidate ->
      val item = popup.itemAt(candidate)
      item != null && item.isColumn && item.pinned == pinned
    }
  }

  /** Where a drag from [from] to [to] lands, or null when the list refuses the drop. */
  fun lineAt(from: Int, to: Int): ColumnsListPopup.DropLine? {
    if (from == to || !popup.canMoveRow(from, to)) return null
    // A row travelling down lands after the target, and one travelling up lands before it.
    return ColumnsListPopup.DropLine(to, below = from < to)
  }

  /**
   * Tints the rows a drag may land in, never the ones it may not. `DockableEditorTabbedContainer` and
   * `RunnerContentUi` fill the accepting area the same way and leave the refusal to the no-drop cursor.
   *
   * The color is the drag row background, which `DefaultTreeUI` also uses over content. The docking area
   * background is opaque and would cover the text. [BAND_ALPHA_SCALE] thins it, because the token is
   * tuned for one row and a band covers a whole group.
   */
  fun paintBand(g: Graphics) {
    val rows = bandRows(draggedRow)
    if (rows.isEmpty()) return
    val first = list.getCellBounds(rows.first(), rows.first()) ?: return
    val last = list.getCellBounds(rows.last(), rows.last()) ?: return
    val band = first.union(last)
    val visible = list.visibleRect
    // Resolved here rather than in a constant, so a theme change reaches the band.
    val rowBackground = JBUI.CurrentTheme.DragAndDrop.ROW_BACKGROUND
    g.color = ColorUtil.toAlpha(rowBackground, (rowBackground.alpha * BAND_ALPHA_SCALE).toInt())
    g.fillRect(visible.x, band.y, visible.width, band.height)
  }

  /**
   * Paints the line where the dragged row lands, which the platform would draw through
   * `DnDEvent.setHighlighting`. That cannot reach a popup: `DnDManagerImpl.getLayeredPane` resolves a
   * `JFrame` or a `JDialog` only, and a popup of its own window is a `JWindow`. The color is the platform
   * drag border, so the line matches every other list.
   */
  fun paintLine(g: Graphics) {
    val line = dropLine ?: return
    val bounds = list.getCellBounds(line.row, line.row) ?: return
    val height = JBUIScale.scale(DROP_LINE_HEIGHT)
    val y = if (line.below) bounds.y + bounds.height - height else bounds.y
    val visible = list.visibleRect
    g.color = JBUI.CurrentTheme.DragAndDrop.BORDER_COLOR
    g.fillRect(visible.x, y, visible.width, height)
  }
}
