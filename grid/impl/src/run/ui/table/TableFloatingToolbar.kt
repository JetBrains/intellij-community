package com.intellij.database.run.ui.table

import com.intellij.codeInsight.hint.HintUtil.getHintBorderColor
import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridUtil
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.CustomizableActionGroupProvider
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.SeparatorAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.MoreActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.checkCanceled
import com.intellij.ui.ClientProperty
import com.intellij.ui.ComponentUtil
import com.intellij.ui.HintHint
import com.intellij.ui.JBColor
import com.intellij.ui.LightweightHint
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.preferredHeight
import com.intellij.ui.util.preferredWidth
import com.intellij.util.io.await
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference
import javax.swing.BorderFactory
import javax.swing.SwingUtilities

class TableFloatingToolbar(private val tableResultView: TableResultView, private val grid: DataGrid, private val cs: CoroutineScope) {

  data class ToolbarPosition(val rightBottomCorner: Point, val rowNum: Int, val columnNum: Int)

  class CustomizableGroupProvider : CustomizableActionGroupProvider() {
    override fun registerGroups(registrar: CustomizableActionGroupRegistrar) {
      registrar.addCustomizableActionGroup(ACTION_GROUP_ID, actionGroupTitle)
    }
  }

  private val actionGroup = DefaultActionGroup(
    CustomActionsSchema.getInstance().getCorrectedAction(ACTION_GROUP_ID) as ActionGroup,
    MoreActionGroup().apply {
      (ActionManager.getInstance().getAction("Console.TableResult.FloatingToolbar.MoreGroup") as? ActionGroup)?.let {
        addAll(it)
      }
    }
  )

  private val actionToolbar = object : ActionToolbarImpl(ACTION_PLACE, actionGroup, true) {
    override fun actionsUpdated(forced: Boolean, newVisibleActions: List<AnAction>) {
      super.actionsUpdated(forced, newVisibleActions)
      hintUpdate()
    }
  }.apply {
    layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
    component.border = JBUI.Borders.empty()
    targetComponent = tableResultView
    isReservePlaceAutoPopupIcon = false
    ClientProperty.put(this, ActionToolbarImpl.SUPPRESS_FAST_TRACK, true)
  }

  private val panel = BorderLayoutPanel().addToCenter(actionToolbar.component).apply {
    border = BorderFactory.createEtchedBorder()
  }
  private val hint = LightweightHint(panel).apply {
    setForceLightweightPopup(true)
  }
  private val hintOptions: HintHint = HintHint()
    .setRequestFocus(false)
    .setTextBg(JBColor.background())
    .setBorderColor(getHintBorderColor())
    .setTextFg(JBColor.foreground())
  private var position = ToolbarPosition(Point(0, 0), 0, 0)

  /** The table the hint is shown in and whose coordinates [position] is expressed in. */
  private var anchorView: TableResultView = tableResultView
  private var showingJob: AtomicReference<Job?> = AtomicReference(null)

  private fun hintUpdate() {
    if (!hint.isVisible) {
      return
    }
    if (!actionToolbar.actions.any { it !is SeparatorAction && it !is MoreActionGroup }) {
      hide()
      return
    }

    hint.pack()
    hint.updateLocation(position.rightBottomCorner.x - hint.component.width, position.rightBottomCorner.y - hint.component.height)
  }

  private fun cornerPositionForCell(cell: Rectangle): Point = Point(
    cell.x + cell.width,
    cell.y,
  )

  private val mouseListener: MouseAdapter = object : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
      hide()
    }

    override fun mouseClicked(e: MouseEvent) {
      val view = e.component as? TableResultView ?: return
      if (e.button != MouseEvent.BUTTON1 || view.selectedRowCount != 1) {
        return
      }

      val columnNum = view.columnAtPoint(e.point)
      val rowNum = view.rowAtPoint(e.point)
      if (rowNum < 0 || rowNum >= view.rowCount || columnNum < 0 || columnNum >= view.columnCount) {
        return
      }

      val cellRect = view.getCellRect(rowNum, columnNum, true)
      val newCornerPosition = cornerPositionForCell(cellRect)
      anchorView = view
      position = ToolbarPosition(newCornerPosition, rowNum, columnNum)
      show()
    }

    override fun mouseMoved(e: MouseEvent) {
      if (!isHintVisible()) {
        return
      }
      // The pointer may already have crossed into the other table, while the hint stays where it was shown.
      val point = if (e.component === anchorView) e.point else SwingUtilities.convertPoint(e.component, e.point, anchorView)
      val cellRectArea = anchorView.getCellRect(position.rowNum, position.columnNum, true)
      cellRectArea.grow(0, cellRectArea.height * 2)
      if (cellRectArea.contains(point)) {
        return
      }

      val distSq = distanceSq(point,
                              Rectangle(Point(position.rightBottomCorner.x - hint.size.width, position.rightBottomCorner.y - hint.size.height), hint.size))
      if (distSq > hidingDistanceSq) {
        hide()
      }
      super.mouseMoved(e)
    }
  }

  private val focusListener: FocusAdapter = object : FocusAdapter() {
    override fun focusLost(e: FocusEvent?) {
      hide()
    }
  }

  private val componentListener: ComponentAdapter = object : ComponentAdapter() {
    override fun componentMoved(e: ComponentEvent?) {
      hide()
    }
  }

  init {
    tableResultView.whenDisposed { hide() }
    attach(tableResultView)
  }

  /**
   * Shares the main table's toolbar with [view], including cells rendered in the pinned strip.
   */
  fun attach(view: TableResultView) {
    view.addMouseListener(mouseListener)
    view.addMouseMotionListener(mouseListener)
    view.addFocusListener(focusListener)
    view.addComponentListener(componentListener)
  }

  /** Stops offering the toolbar in [view], which is about to be discarded, and takes the hint down if it is anchored there. */
  fun detach(view: TableResultView) {
    view.removeMouseListener(mouseListener)
    view.removeMouseMotionListener(mouseListener)
    view.removeFocusListener(focusListener)
    view.removeComponentListener(componentListener)
    if (anchorView !== view) return
    hide()
    anchorView = tableResultView
  }

  private fun isHintVisible(): Boolean {
    return hint.isVisible && hint.size.height != 0 && hint.size.width != 0
  }

  private fun isDisabled(): Boolean = GridUtil.getSettings(grid)?.isDisableGridFloatingToolbar ?: true

  fun show() {
    if (isDisabled()) {
      return
    }

    showingJob.set(cs.launch(Dispatchers.EDT) {
      ComponentUtil.markAsShowing(actionToolbar.component, true)
      val future = actionToolbar.updateActionsAsync()
      try {
        future.await()
      }
      catch (_: Exception) {
      }
      finally {
        ComponentUtil.markAsShowing(actionToolbar.component, false)
      }

      val hasVisibleAction = actionToolbar.actions.any { it !is SeparatorAction && it !is MoreActionGroup }
      if (!hasVisibleAction) {
        return@launch
      }

      checkCanceled()
      hint.show(anchorView,
                position.rightBottomCorner.x - hint.component.preferredWidth,
                position.rightBottomCorner.y - hint.component.preferredHeight,
                anchorView,
                hintOptions)
    })
  }

  fun hide() {
    showingJob.get()?.cancel()
    hint.hide()
  }

  /** The table and the cell the toolbar was last offered for. */
  @TestOnly
  fun getOfferedCell(): Pair<TableResultView, ToolbarPosition> = anchorView to position

  companion object {
    const val ACTION_PLACE = "TableFloatingToolbar"
    const val ACTION_GROUP_ID = "Console.TableResult.FloatingToolbarGroup"
    val actionGroupTitle = DataGridBundle.message("Console.TableResult.FloatingToolbarGroup.title")

    val hidingDistanceSq by lazy { JBUIScale.scale(100) * JBUIScale.scale(100) }

    private fun distanceSq(p: Point, r: Rectangle): Double {
      if (r.contains(p)) {
        return 0.0
      }

      val dMinX = p.x - r.minX
      val dMaxX = p.x - r.maxX
      val dx = if (dMaxX > 0) dMaxX else (if (dMinX > 0) 0.0 else -dMinX)

      val dMinY = p.y - r.minY
      val dMaxY = p.y - r.maxY
      val dy = if (dMaxY > 0) dMaxY else (if (dMinY > 0) 0.0 else -dMinY)

      return dx * dx + dy * dy
    }
  }
}
