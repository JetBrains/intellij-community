package com.intellij.database.run.ui.columns

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.DataGridListener
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.run.actions.NO_SPACE
import com.intellij.database.run.actions.TRANSPOSED
import com.intellij.database.run.actions.actablePanel
import com.intellij.database.run.actions.allPinned
import com.intellij.database.run.actions.pinPanel
import com.intellij.database.run.actions.showReason
import com.intellij.icons.AllIcons
import com.intellij.ide.dnd.DnDDragStartBean
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDImage
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.SmoothAutoScroller
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.GridColumnPinning
import com.intellij.database.run.ui.TableResultPanel
import com.intellij.database.run.ui.table.ColumnPinning
import com.intellij.database.run.ui.table.TableResultView
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColorUtil
import com.intellij.ui.PopupHandler
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBList
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Component
import java.awt.AlphaComposite
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.Point
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.TransferHandler
import javax.swing.ToolTipManager
import javax.swing.ScrollPaneConstants
import javax.swing.event.DocumentEvent

/**
 * The column list popup. It searches the columns of [grid], and it hides or shows them.
 *
 * Every change applies at once, so the popup has no OK button and no Cancel button.
 */
@ApiStatus.Internal
class ColumnsListPopup(private val grid: DataGrid) {
  private var model = ColumnsListModel(buildColumnsListItems(grid))

  /** True while this popup changes the grid, so the grid events it causes do not rebuild the rows one by one. */
  private var applying = false

  private val listModel = CollectionListModel<Row>()

  /**
   * A document grid is left out for now. Only [com.intellij.database.datagrid.DocumentDataHookUp] implements
   * a column move in the data, so its header drag writes the file, and the popup must not disagree with the header.
   * Every other grid reorders its view, which is what its header drag does too.
   */
  private val reorderable: Boolean = GridUtil.getDocumentDataHookUp(grid) == null

  private val renderer = ItemRenderer(ColumnPinning.isEnabled(), reorderable)

  /** Where the dragged row lands, or null while no drag is over the list. */
  private var dropLine: DropLine? = null

  /** The spacer row that holds the line between the pinned columns and the rest, or -1 when there is none. */
  private var separatorRow = -1

  private val list = object : JBList<Row>(listModel) {
    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      paintDropBand(g)
      paintPinnedGroupLine(g)
      paintDropLine(g)
    }

    /** Explains a pin that cannot be taken. A disabled control has no other way to say why. */
    override fun getToolTipText(event: MouseEvent): String? {
      val row = locationToIndex(event.point)
      if (row < 0 || getCellBounds(row, row)?.contains(event.point) != true) return null
      val item = itemAt(row) ?: return null
      if (item.canTogglePin || controlAt(event.x, row) != RowControl.PIN) return null
      return DataGridBundle.message("action.Console.TableResult.PinColumns.insufficient.space.description")
    }
  }.apply {
    selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    cellRenderer = renderer
    emptyText.text = DataGridBundle.message("action.Console.TableResult.ColumnsList.Popup.NoMatch")
    emptyText.appendSecondaryText(
      DataGridBundle.message("action.Console.TableResult.ColumnsList.Popup.ClearSearch"),
      SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
    ) { searchField.text = "" }
  }

  private val searchField = SearchTextField(false).apply {
    textEditor.emptyText.text = DataGridBundle.message("action.Console.TableResult.ColumnsList.Popup.SearchPlaceholder")
  }

  private val counter = JLabel().apply { foreground = UIUtil.getContextHelpForeground() }

  private val showAllLink = ActionLink("") { applyVisibility(model.matched, true) }

  private val hideAllLink = ActionLink("") { applyVisibility(model.matched, false) }

  private val content: JComponent = BorderLayoutPanel().apply {
    border = JBUI.Borders.empty(POPUP_PAD)
    preferredSize = Dimension(JBUIScale.scale(DEFAULT_WIDTH), JBUIScale.scale(DEFAULT_HEIGHT))
    addToTop(searchField)
    addToCenter(ScrollPaneFactory.createScrollPane(
      list,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
    ).apply { border = JBUI.Borders.empty(6, 0) })
    addToBottom(BorderLayoutPanel().apply {
      addToCenter(counter)
      addToRight(JPanel(BorderLayout(JBUIScale.scale(12), 0)).apply {
        isOpaque = false
        add(showAllLink, BorderLayout.WEST)
        add(hideAllLink, BorderLayout.EAST)
      })
    })
  }

  /** The component of the popup, which a test drives without showing the popup. */
  val component: JComponent get() = content

  val popup: JBPopup = JBPopupFactory.getInstance()
    .createComponentPopupBuilder(content, searchField.textEditor)
    .setProject(grid.project)
    .setRequestFocus(true)
    .setResizable(true)
    .setMovable(true)
    .setDimensionServiceKey(grid.project, DIMENSION_KEY, false)
    .setMinSize(Dimension(JBUIScale.scale(260), JBUIScale.scale(200)))
    .createPopup()

  init {
    searchField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) = search()
    })
    installKeys()
    installMouse()
    installHover()
    installContextMenu()
    ToolTipManager.sharedInstance().registerComponent(list)
    list.addListSelectionListener { skipSeparatorSelection() }
    if (reorderable) installDrag()
    grid.addDataGridListener(object : DataGridListener {
      override fun onContentChanged(dataGrid: DataGrid, place: GridRequestSource.RequestPlace?) {
        if (!applying) refresh()
      }
    }, popup)
    refresh()
  }

  /**
   * Tracks the row under the pointer.
   *
   * The popup does this rather than install [com.intellij.ui.hover.ListHoverListener], because
   * `WideSelectionListUI` paints a hover background for whichever row that listener reports, before it
   * consults the row itself. The separator row must react to nothing, and it cannot opt out of that paint.
   */
  private fun installHover() {
    val listener = object : MouseAdapter() {
      override fun mouseMoved(e: MouseEvent) = setHoveredRow(hoverableRowAt(e.point))

      override fun mouseEntered(e: MouseEvent) = setHoveredRow(hoverableRowAt(e.point))

      override fun mouseExited(e: MouseEvent) = setHoveredRow(-1)
    }
    list.addMouseListener(listener)
    list.addMouseMotionListener(listener)
  }

  /** The row under [point] when that row can react to a pointer, and -1 for the separator or for no row. */
  private fun hoverableRowAt(point: Point): Int {
    val row = list.locationToIndex(point)
    if (row < 0 || list.getCellBounds(row, row)?.contains(point) != true) return -1
    return if (itemAt(row) == null) -1 else row
  }

  private fun setHoveredRow(row: Int) {
    val previous = renderer.hoveredRow
    if (previous == row) return
    renderer.hoveredRow = row
    repaintRow(previous)
    repaintRow(row)
  }

  private fun repaintRow(row: Int) {
    val bounds = if (row < 0) null else list.getCellBounds(row, row)
    if (bounds != null) list.repaint(0, bounds.y, list.width, bounds.height)
  }

  /** The separator is a row of its own, so the selection steps over it instead of landing on it. */
  private fun skipSeparatorSelection() {
    val index = list.selectedIndex
    if (index < 0 || listModel.items.getOrNull(index) !is Row.Separator) return
    val next = if (index + 1 < listModel.size) index + 1 else index - 1
    if (next >= 0) list.selectedIndex = next else list.clearSelection()
  }

  private fun search() {
    model = model.withFilter(ColumnsListFilter(text = searchField.text.trim()))
    updateRows()
  }

  /** Reads the grid again, because the grid is the one source of truth for the visibility and the order. */
  private fun refresh(previous: List<ModelIndex<GridColumn>> = model.rows.mapNotNull { it.modelIndex }) {
    // The order the list shows now decides where a hidden row stays, so it goes into the rebuild.
    model = ColumnsListModel(buildColumnsListItems(grid, previous), model.filter, model.order)
    updateRows()
  }

  private fun updateRows() {
    val selected = list.selectedValuesList.filterIsInstance<Row.Item>().mapTo(HashSet()) { it.value.name }
    val separatorAt = firstRowAfterPinned()
    val rows = buildList {
      model.rows.forEachIndexed { index, item ->
        if (index == separatorAt) add(Row.Separator)
        add(Row.Item(item))
      }
    }
    separatorRow = rows.indexOfFirst { it is Row.Separator }
    listModel.replaceAll(rows)
    rows.forEachIndexed { index, row ->
      if (row is Row.Item && row.value.name in selected) list.addSelectionInterval(index, index)
    }
    counter.text = DataGridBundle.message(
      "action.Console.TableResult.ColumnsList.Popup.Counter", model.shownCount, model.totalCount
    )
    val searching = !model.filter.isEmpty
    showAllLink.text = DataGridBundle.message(
      if (searching) "action.Console.TableResult.ColumnsList.Popup.ShowMatching"
      else "action.Console.TableResult.ColumnsList.Popup.ShowAll"
    )
    hideAllLink.text = DataGridBundle.message(
      if (searching) "action.Console.TableResult.ColumnsList.Popup.HideMatching"
      else "action.Console.TableResult.ColumnsList.Popup.HideAll"
    )
    showAllLink.isEnabled = model.matched.any { !it.visible }
    hideAllLink.isEnabled = hideable(model.matched).isNotEmpty()
  }

  /**
   * The row that carries the separator above it, which is the first row after the pinned group.
   * A-Z order sorts a pinned column in with the rest, so it has no group to close.
   */
  private fun firstRowAfterPinned(): Int {
    if (model.order != ColumnsListOrder.GRID) return -1
    val last = model.rows.indexOfLast { it.pinned }
    return if (last in 0 until model.rows.lastIndex) last + 1 else -1
  }

  /**
   * Hides or shows every column of [items].
   * A hide keeps the last shown column, because the grid needs one column to render.
   */
  private fun applyVisibility(items: List<ColumnsListItem>, visible: Boolean) {
    val targets = if (visible) items.filter { !it.visible } else hideable(items)
    if (targets.isEmpty()) return
    applying = true
    try {
      for (item in targets) {
        val columnIdx = item.modelIndex ?: continue
        grid.setColumnEnabled(columnIdx, visible)
      }
    }
    finally {
      applying = false
    }
    refresh()
  }

  /** The columns of [items] that a hide may take, which leaves the grid with one shown column at least. */
  private fun hideable(items: List<ColumnsListItem>): List<ColumnsListItem> {
    val shown = items.filter { it.visible }
    if (shown.size < model.shownCount) return shown
    return shown.dropLast(1)
  }

  /** The column of the list row at [index], or null for the separator row. */
  private fun itemAt(index: Int): ColumnsListItem? =
    (listModel.items.getOrNull(index) as? Row.Item)?.value

  private fun toggleSelected() {
    val selected = list.selectedValuesList.filterIsInstance<Row.Item>().map { it.value }.filter { it.isColumn }
    if (selected.isEmpty()) return
    applyVisibility(selected, !selected.first().visible)
  }

  /**
   * Whether a drag may take the list row at [from] to [to].
   *
   * A row moves inside its own group. A pinned column reorders among the pinned columns and an unpinned
   * one among the rest, because the two groups render in two different tables.
   *
   * A hidden row is a place to drop on, because the list keeps a place for it and that place decides where
   * the column comes back. A hidden row does not travel itself, which spares the user a drag that moves
   * nothing they can see.
   */
  fun canMoveRow(from: Int, to: Int): Boolean {
    if (!reorderable || model.order != ColumnsListOrder.GRID) return false
    val source = itemAt(from) ?: return false
    val target = itemAt(to) ?: return false
    if (!source.isColumn || !source.visible) return false
    if (from == to) return true
    return target.isColumn && source.pinned == target.pinned
  }

  /**
   * Moves the list row at [from] to [to].
   *
   * The list owns the whole order, hidden columns included. The grid takes the part of it that it can
   * show, and the hidden columns keep their new places for when they come back. A move that only steps
   * over a hidden row therefore leaves the table looking the same, and still means something.
   */
  fun moveRow(from: Int, to: Int) {
    if (!canMoveRow(from, to)) return
    val source = itemAt(from) ?: return
    val target = itemAt(to) ?: return
    val rows = model.rows.toMutableList()
    val sourcePos = rows.indexOf(source)
    val targetPos = rows.indexOf(target)
    if (sourcePos < 0 || targetPos < 0 || sourcePos == targetPos) return

    rows.removeAt(sourcePos)
    rows.add(targetPos, source)
    val order = rows.mapNotNull { it.modelIndex }
    (grid as? TableResultPanel)?.setColumnsDisplayOrder(order)
    rememberHiddenPlaces(rows)
    refresh(order)
  }

  /**
   * Tells the table where every hidden column now belongs.
   *
   * A hidden column comes back after the shown column that precedes it here. Without this a move across a
   * hidden row would look right until that column came back somewhere else.
   */
  private fun rememberHiddenPlaces(rows: List<ColumnsListItem>) {
    val view = grid.resultView as? TableResultView ?: return
    var lastShown = TableResultView.NO_LEFT_NEIGHBOUR
    for (item in rows) {
      val column = item.modelIndex ?: continue
      if (item.visible) lastShown = column.asInteger()
      else view.rememberHiddenColumnPlace(column.asInteger(), lastShown)
    }
  }

  /** Pins the column of [item], or unpins it when it is pinned already. */
  private fun togglePin(item: ColumnsListItem) {
    if (!item.canTogglePin) return
    val columnIdx = item.modelIndex ?: return
    val pinning = grid as? GridColumnPinning ?: return
    val columns = ModelIndexSet.forColumns(grid, columnIdx.asInteger())
    // pinColumns refuses a pin that would leave no width for the other columns. An unpin always applies.
    if (item.pinned) pinning.setColumnsPinned(columns, false) else pinning.pinColumns(columns)
    refresh()
  }

  private fun navigate(item: ColumnsListItem) {
    val columnIdx: ModelIndex<GridColumn> = item.modelIndex ?: return
    if (!item.visible) applyVisibility(listOf(item), true)
    grid.selectionModel.setColumnSelection(columnIdx, true)
  }

  /**
   * The rows a copy takes: the selected columns, or every matched column when nothing is selected.
   * The separator never counts, and the order is the order of the list.
   */
  private fun copyTargets(): List<ColumnsListItem> {
    val selected = list.selectedValuesList.filterIsInstance<Row.Item>().map { it.value }.filter { it.isColumn }
    if (selected.isNotEmpty()) return selected
    return listModel.items.filterIsInstance<Row.Item>().map { it.value }.filter { it.isColumn }
  }

  /** Copies the names, separated by a comma, the way the Copy Column Name action of the header does. */
  fun copyNames() {
    val names = copyTargets().map { it.name }
    if (names.isEmpty()) return
    CopyPasteManager.getInstance().setContents(StringSelection(names.joinToString(",")))
  }

  /** Copies one column per line, with a tab between the name and the type. */
  fun copyNamesAndTypes() {
    val rows = copyTargets()
    if (rows.isEmpty()) return
    val text = rows.joinToString("\n") { item ->
      if (item.typeText == null) item.name else "${item.name}\t${item.typeText}"
    }
    CopyPasteManager.getInstance().setContents(StringSelection(text))
  }

  /** Whether the grid shows its columns in the order the data has. */
  fun isOriginalOrder(): Boolean = (grid as? TableResultPanel)?.isColumnsOrderModified() != true

  /** Puts the shown columns back in the order the data has, and keeps the pins and the hidden columns. */
  fun restoreOriginalOrder() {
    val panel = grid as? TableResultPanel ?: return
    panel.restoreNaturalColumnsOrder()
    // A hidden row goes back with the rest, so the rebuild starts from the data order and not from the list.
    refresh(grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).columnIndices.asList())
  }

  fun hasPinnedColumns(): Boolean =
    ColumnPinning.isEnabled() && (grid as? GridColumnPinning)?.hasPinnedColumns() == true

  fun unpinAllColumns() {
    val pinning = grid as? GridColumnPinning ?: return
    pinning.unpinAllColumns()
    refresh()
  }

  /** The columns a pin action targets, which are the selected rows. */
  private fun selectedColumns(): ModelIndexSet<GridColumn> {
    val indices = list.selectedValuesList.filterIsInstance<Row.Item>().mapNotNull { it.value.modelIndex?.asInteger() }
    return ModelIndexSet.forColumns(grid, *indices.toIntArray())
  }

  private fun installContextMenu() {
    // A right click outside the selection acts on the row under the pointer, as a list is expected to.
    list.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) = selectRowForPopup(e)

      override fun mouseReleased(e: MouseEvent) = selectRowForPopup(e)

      private fun selectRowForPopup(e: MouseEvent) {
        if (!e.isPopupTrigger) return
        val row = list.locationToIndex(e.point)
        if (row < 0 || itemAt(row) == null || list.isSelectedIndex(row)) return
        list.selectedIndex = row
      }
    })
    PopupHandler.installPopupMenu(list, contextMenuGroup(), ACTION_PLACE)
  }

  /**
   * The right click menu.
   *
   * The pin items keep the rules of the column header, through the helpers that header uses, so a pin
   * refuses for the same reasons and says the same thing. They act on the rows selected here.
   */
  fun contextMenuGroup(): ActionGroup =
    DefaultActionGroup(
      CopyAction(false),
      CopyAction(true),
      Separator.getInstance(),
      RestoreOriginalOrderAction(),
      Separator.getInstance(),
      PinSelectedAction(),
      UnpinSelectedAction(),
      PinUpToHereAction(),
      UnpinAllAction(),
    )

  private abstract class ListAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  /** Copies the names, and the types too when [withTypes]. The wording follows the number of columns. */
  private inner class CopyAction(private val withTypes: Boolean) : ListAction() {
    override fun update(e: AnActionEvent) {
      val one = copyTargets().size == 1
      e.presentation.text = DataGridBundle.message(
        when {
          withTypes && one -> "action.Console.TableResult.ColumnsList.Popup.CopyNameAndType"
          withTypes -> "action.Console.TableResult.ColumnsList.Popup.CopyNamesAndTypes"
          one -> "action.Console.TableResult.ColumnsList.Popup.CopyName"
          else -> "action.Console.TableResult.ColumnsList.Popup.CopyNames"
        }
      )
      e.presentation.isEnabledAndVisible = copyTargets().isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
      if (withTypes) copyNamesAndTypes() else copyNames()
    }
  }

  private inner class RestoreOriginalOrderAction : ListAction() {
    override fun update(e: AnActionEvent) {
      e.presentation.text = DataGridBundle.message("action.Console.TableResult.ColumnsList.Popup.RestoreOriginalOrder")
      e.presentation.isEnabledAndVisible = !isOriginalOrder()
    }

    override fun actionPerformed(e: AnActionEvent) = restoreOriginalOrder()
  }

  private inner class PinSelectedAction : ListAction() {
    override fun update(e: AnActionEvent) {
      val columns = selectedColumns()
      val one = columns.size() == 1
      e.presentation.text = DataGridBundle.message(
        if (one) "action.Console.TableResult.PinColumn.text" else "action.Console.TableResult.PinColumns.text"
      )
      val panel = pinPanel(grid)
      if (panel == null || columns.size() == 0 || !allPinned(panel, columns, false)) {
        e.presentation.isEnabledAndVisible = false
        return
      }
      if (grid.resultView.isTransposed) {
        showReason(e, DataGridBundle.message(TRANSPOSED))
        return
      }
      showReason(e, if (panel.pinnedColumnsFit(columns)) null else DataGridBundle.message(NO_SPACE))
    }

    override fun actionPerformed(e: AnActionEvent) {
      actablePanel(grid)?.pinColumns(selectedColumns())
      refresh()
    }
  }

  private inner class UnpinSelectedAction : ListAction() {
    override fun update(e: AnActionEvent) {
      val columns = selectedColumns()
      val one = columns.size() == 1
      e.presentation.text = DataGridBundle.message(
        if (one) "action.Console.TableResult.UnpinColumn.text" else "action.Console.TableResult.UnpinColumns.text"
      )
      val panel = pinPanel(grid)
      if (panel == null || columns.size() == 0 || !allPinned(panel, columns, true)) {
        e.presentation.isEnabledAndVisible = false
        return
      }
      showReason(e, if (grid.resultView.isTransposed) DataGridBundle.message(TRANSPOSED) else null)
    }

    override fun actionPerformed(e: AnActionEvent) {
      actablePanel(grid)?.setColumnsPinned(selectedColumns(), false)
      refresh()
    }
  }

  private inner class PinUpToHereAction : ListAction() {
    override fun update(e: AnActionEvent) {
      e.presentation.text = DataGridBundle.message("action.Console.TableResult.PinColumnsUpToHere.text")
      val panel = pinPanel(grid)
      val column = selectedColumns().asIterable().singleOrNull()
      if (panel == null || column == null || !panel.canPinColumnsUpToHere(column)) {
        e.presentation.isEnabledAndVisible = false
        return
      }
      if (grid.resultView.isTransposed) {
        showReason(e, DataGridBundle.message(TRANSPOSED))
        return
      }
      showReason(e, if (panel.pinnedColumnsUpToHereFit(column)) null else DataGridBundle.message(NO_SPACE))
    }

    override fun actionPerformed(e: AnActionEvent) {
      val column = selectedColumns().asIterable().singleOrNull() ?: return
      actablePanel(grid)?.pinColumnsUpToHere(column)
      refresh()
    }
  }

  private inner class UnpinAllAction : ListAction() {
    override fun update(e: AnActionEvent) {
      e.presentation.text = DataGridBundle.message("action.Console.TableResult.UnpinAllColumns.text")
      e.presentation.isEnabledAndVisible = hasPinnedColumns()
    }

    override fun actionPerformed(e: AnActionEvent) = unpinAllColumns()
  }

  private fun installKeys() {
    list.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), TOGGLE_ACTION)
    list.actionMap.put(TOGGLE_ACTION, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) = toggleSelected()
    })
    list.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, MENU_SHORTCUT_MASK), COPY_ACTION)
    list.actionMap.put(COPY_ACTION, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) = copyNames()
    })
    list.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), NAVIGATE_ACTION)
    list.actionMap.put(NAVIGATE_ACTION, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        itemAt(list.selectedIndex)?.let { navigate(it) }
        popup.cancel()
      }
    })
    searchField.textEditor.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), FOCUS_LIST_ACTION)
    searchField.textEditor.actionMap.put(FOCUS_LIST_ACTION, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        if (listModel.isEmpty) return
        if (list.selectedIndex < 0) list.selectedIndex = 0
        list.requestFocusInWindow()
      }
    })
  }

  private fun installMouse() {
    list.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        val row = list.locationToIndex(e.point)
        if (row < 0 || list.getCellBounds(row, row)?.contains(e.point) != true) return
        val item = itemAt(row) ?: return
        when {
          onCheckBox(e, row, item) -> applyVisibility(listOf(item), !item.visible)
          controlAt(e.x, row) == RowControl.PIN && item.isColumn -> togglePin(item)
          // The grip reads as a control, so a plain click on it moves nothing.
          controlAt(e.x, row) == RowControl.GRIP -> Unit
          item.isColumn -> navigate(item)
        }
      }
    })
  }

  /** The control under [x], which both sit at the right end of the row. */
  private fun controlAt(x: Int, row: Int): RowControl? {
    val bounds = list.getCellBounds(row, row) ?: return null
    val gripEnd = bounds.x + bounds.width - JBUIScale.scale(CONTROLS_RIGHT_PAD)
    val gripStart = gripEnd - GRIP_WIDTH
    val pinStart = gripStart - PIN_WIDTH - JBUIScale.scale(CONTROL_GAP)
    return when (x) {
      in gripStart until gripEnd -> RowControl.GRIP
      in pinStart until gripStart -> RowControl.PIN
      else -> null
    }
  }

  private fun onCheckBox(e: MouseEvent, row: Int, item: ColumnsListItem): Boolean {
    val bounds = list.getCellBounds(row, row) ?: return false
    val left = bounds.x + indent(item)
    return e.x >= left && e.x <= left + checkBoxWidth
  }

  private class ItemRenderer(
    private val pinningEnabled: Boolean,
    private val reorderable: Boolean,
  ) : ListCellRenderer<Row> {
    /** The row under the pointer, or -1. The popup keeps this, because the list paints no hover of its own. */
    var hoveredRow: Int = -1

    private val checkBox = JCheckBox().apply { isOpaque = false }
    private val text = SimpleColoredComponent().apply { isOpaque = false }
    private val pin = iconLabel(AllIcons.General.Pin, CONTROL_GAP)
    /** Tells the user that the hovered row can be dragged. The drag itself starts anywhere on the row. */
    private val grip = iconLabel(AllIcons.General.Drag, 0)

    /**
     * The two controls keep their room whether they show an icon or not.
     * The row therefore holds still when the pointer enters it, instead of reflowing the name.
     */
    private val controls = BorderLayoutPanel().apply {
      isOpaque = false
      border = JBUI.Borders.empty(0, CONTROLS_LEFT_PAD, 0, CONTROLS_RIGHT_PAD)
      addToCenter(pin)
      addToRight(grip)
    }

    /** Paints the row. The separator is not part of it, so a selection never reaches the line. */
    private val content = BorderLayoutPanel().apply {
      border = JBUI.Borders.empty(1, 0)
      addToLeft(checkBox)
      addToCenter(text)
      addToRight(controls)
    }

    /**
     * The spacer that holds the line between the pinned columns and the rest.
     * It draws nothing. The list paints the line, so the row can carry no hover and no selection.
     */
    private val separator = JPanel().apply {
      isOpaque = false
      preferredSize = Dimension(0, JBUIScale.scale(SEPARATOR_HEIGHT))
      // WideSelectionListUI raises every row to the platform row height unless a row opts out here.
      putClientProperty(JBList.IGNORE_LIST_ROW_HEIGHT, true)
    }

    override fun getListCellRendererComponent(
      list: JList<out Row>,
      value: Row,
      index: Int,
      selected: Boolean,
      focused: Boolean,
    ): Component {
      if (value is Row.Separator) return separator
      val item = (value as Row.Item).value
      return renderItem(item, index, selected)
    }

    private fun renderItem(value: ColumnsListItem, index: Int, selected: Boolean): Component {
      checkBox.isSelected = value.visible
      checkBox.border = JBUI.Borders.emptyLeft(indent(value))
      text.clear()
      text.icon = value.icon
      text.append(value.name, if (value.visible) SimpleTextAttributes.REGULAR_ATTRIBUTES else SimpleTextAttributes.GRAYED_ATTRIBUTES)
      value.typeText?.let { text.append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
      val hovered = index == hoveredRow
      pin.icon = when {
        !pinningEnabled || !value.isColumn -> null
        !value.pinned && !hovered -> null
        value.canTogglePin -> AllIcons.General.Pin
        else -> DISABLED_PIN
      }
      grip.icon = if (reorderable && value.isColumn && hovered) AllIcons.General.Drag else null
      content.isOpaque = true
      content.background = if (selected) UIUtil.getListSelectionBackground(true) else UIUtil.getListBackground()
      text.foreground = if (selected) UIUtil.getListSelectionForeground(true) else UIUtil.getListForeground()
      return content
    }
  }

  /** A row of the list. The separator is a row of its own, so it joins no hover and no selection. */
  sealed interface Row {
    class Item(val value: ColumnsListItem) : Row

    object Separator : Row
  }

  /**
   * Installs the drag.
   *
   * The list keeps its own support rather than [com.intellij.ui.RowsDnDSupport], because that helper sets
   * no image provider, so nothing follows the cursor. Everything inside the builder is the shape the
   * platform uses.
   *
   * The whole row is the drag source. The grip only tells the user that the hovered row can be dragged.
   */
  private fun installDrag() {
    // RowsDnDSupport installs these two before it builds. Without them the drop target never draws.
    list.transferHandler = TransferHandler(null)
    SmoothAutoScroller.installDropTargetAsNecessary(list)
    DnDSupport.createBuilder(list)
      .setBeanProvider { info -> dragSource(info.point)?.let { DnDDragStartBean(it, info.point) } }
      .setImageProvider { info -> dragSource(info.point)?.let { dragImage(it, info.point) } }
      .setTargetChecker { event -> checkDrop(event) }
      .setDropHandler { event -> dropRow(event) }
      .setCleanUpOnLeaveCallback { showDropLine(null) }
      .setDropEndedCallback { endDrag() }
      .setDisposableParent(popup)
      .install()
  }

  /** The row the drag carries, or -1 while no drag is over the list. It lights the band it may land in. */
  private var draggedRow = -1

  /** The row a drag may start from, which is any draggable row under [point]. */
  private fun dragSource(point: Point): Int? {
    val row = list.locationToIndex(point)
    if (row < 0 || list.getCellBounds(row, row)?.contains(point) != true) return null
    return if (canMoveRow(row, row)) row else null
  }

  /**
   * The dragged row, built the way `DnDAwareTree.createDragImage` builds one.
   *
   * The image covers the whole cell rather than the preferred size of the renderer, because a list cell
   * is as wide as the list.
   *
   * The offset keeps the grabbed pixel under the cursor. `DnDManagerImpl` hands the pair to
   * `DragGestureEvent.startDrag`, whose offset is measured from the cursor to the image origin, so the
   * offset is the negated grab point.
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
    val from = event.attachedObject as? Int
    if (from == null) {
      refuseDrop(event)
      return true
    }
    startDrag(from)
    val to = list.locationToIndex(event.point)
    if (to < 0) {
      event.isDropPossible = false
      showDropLine(null)
      return true
    }
    val line = dropLineAt(from, to)
    if (to != from && line == null) {
      event.isDropPossible = false
      showDropLine(null)
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
    val from = event.attachedObject as? Int
    val to = list.locationToIndex(event.point)
    showDropLine(null)
    if (from == null || to < 0) return
    moveRow(from, to)
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

  /**
   * The rows a drag from [row] may land in, which are the rows of the group that [row] belongs to.
   * Empty for no row, for the separator, and for a row that cannot move.
   */
  fun dropBandRows(row: Int): List<Int> {
    if (row < 0) return emptyList()
    val pinned = itemAt(row)?.pinned ?: return emptyList()
    return (0 until listModel.size).filter { row ->
      val item = itemAt(row)
      item != null && item.isColumn && item.pinned == pinned
    }
  }

  /**
   * Tints the rows a drag may land in.
   *
   * The platform tints no area to say that it refuses a drop. It tints the area that accepts one, the way
   * `DockableEditorTabbedContainer` and `RunnerContentUi` fill a docking area, and leaves the refusal to
   * the no-drop cursor. This follows that, so the band says where the row may go rather than where it may
   * not.
   *
   * The color is the row background of a drag, which `DefaultTreeUI` and the filled-rectangle highlighter
   * both use over content. The area background of the docking targets is opaque and would cover the text.
   *
   * The alpha is scaled down by [BAND_ALPHA_SCALE]. The token is tuned for a single row, and a band covers
   * a whole group, so the same alpha reads far heavier over that many rows.
   */
  private fun paintDropBand(g: Graphics) {
    val rows = dropBandRows(draggedRow)
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

  /** Where a drag from [from] to [to] lands, or null when the list refuses the drop. */
  fun dropLineAt(from: Int, to: Int): DropLine? {
    if (from == to || !canMoveRow(from, to)) return null
    // A row travelling down lands after the target, and one travelling up lands before it.
    return DropLine(to, below = from < to)
  }

  private fun showDropLine(line: DropLine?) {
    if (dropLine == line) return
    dropLine = line
    list.repaint()
  }

  /**
   * Paints the line between the pinned columns and the rest.
   *
   * The list paints it for the same reason it paints the drop line. A component inside a row shares the
   * bounds of that row, so a hover or a selection can reach it. This line sits in the middle of a spacer
   * row instead, which leaves the same space above the line and below it.
   */
  private fun paintPinnedGroupLine(g: Graphics) {
    if (separatorRow < 0) return
    val bounds = list.getCellBounds(separatorRow, separatorRow) ?: return
    val height = JBUIScale.scale(SEPARATOR_LINE_HEIGHT)
    val visible = list.visibleRect
    g.color = JBUI.CurrentTheme.Popup.separatorColor()
    g.fillRect(visible.x, bounds.y + (bounds.height - height) / 2, visible.width, height)
  }

  /**
   * Paints the line where the dragged row lands.
   *
   * The platform draws this through `DnDEvent.setHighlighting`, which cannot reach a popup.
   * `DnDManagerImpl.getLayeredPane` resolves a `JFrame` or a `JDialog` only, and a popup of its own
   * window is a `JWindow`, so the highlighter has no layered pane and never appears. The color is the
   * platform drag border, so the line looks the same as the one every other list draws.
   */
  private fun paintDropLine(g: Graphics) {
    val line = dropLine ?: return
    val bounds = list.getCellBounds(line.row, line.row) ?: return
    val height = JBUIScale.scale(DROP_LINE_HEIGHT)
    val y = if (line.below) bounds.y + bounds.height - height else bounds.y
    val visible = list.visibleRect
    g.color = JBUI.CurrentTheme.DragAndDrop.BORDER_COLOR
    g.fillRect(visible.x, y, visible.width, height)
  }

  /** Where a dragged row lands: at the bottom edge of [row] when [below], and at its top edge otherwise. */
  data class DropLine(val row: Int, val below: Boolean)

  /** A control at the right end of a row. */
  private enum class RowControl { PIN, GRIP }

  companion object {
    private const val TOGGLE_ACTION = "columnsListToggle"
    private const val NAVIGATE_ACTION = "columnsListNavigate"
    private const val COPY_ACTION = "columnsListCopyNames"
    private const val ACTION_PLACE = "ColumnsListPopup"

    /** Command on macOS and Control elsewhere, so the copy key matches the platform. */
    private val MENU_SHORTCUT_MASK: Int get() = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
    private const val FOCUS_LIST_ACTION = "columnsListFocusList"
    private const val DIMENSION_KEY = "ColumnsListPopup"
    private const val INDENT = 14
    private const val CONTROLS_LEFT_PAD = 28
    private const val CONTROLS_RIGHT_PAD = 10
    private const val CONTROL_GAP = 10
    private const val POPUP_PAD = 10
    private const val DEFAULT_WIDTH = 420
    private const val DEFAULT_HEIGHT = 480
    private const val DROP_LINE_HEIGHT = 2
    private val DISABLED_PIN: Icon get() = IconLoader.getDisabledIcon(AllIcons.General.Pin)

    /** The spacer row of the pinned group line. The line takes its middle, and the rest is the padding. */
    private const val SEPARATOR_HEIGHT = 7
    private const val SEPARATOR_LINE_HEIGHT = 1
    private const val DRAG_IMAGE_ALPHA = 0.7f

    /** How much of the single-row drag alpha the band keeps. */
    private const val BAND_ALPHA_SCALE = 0.5

    private val checkBoxWidth: Int get() = JCheckBox().preferredSize.width
    private val PIN_WIDTH: Int get() = AllIcons.General.Pin.iconWidth
    private val GRIP_WIDTH: Int get() = AllIcons.General.Drag.iconWidth

  /** A label that keeps the room of [icon] even while it shows none, plus [trailingGap] of space after it. */
    private fun iconLabel(icon: Icon, trailingGap: Int): JLabel = JLabel().apply {
      horizontalAlignment = SwingConstants.LEFT
      preferredSize = Dimension(icon.iconWidth + JBUIScale.scale(trailingGap), icon.iconHeight)
    }

    private fun indent(item: ColumnsListItem): Int = JBUIScale.scale(INDENT) * item.depth

    @JvmStatic
    fun show(grid: DataGrid, e: AnActionEvent) {
      val columnsList = ColumnsListPopup(grid)
      val anchor = if (e.isFromActionToolbar) topOfTable(grid, columnsList) else null
      if (anchor == null) columnsList.popup.showInBestPositionFor(e.dataContext)
      else columnsList.popup.show(anchor)
    }

    /**
     * The top left corner of the popup, so that it opens level with the top of the table and centred on it.
     * A toolbar button alone drops the popup below the toolbar.
     *
     * The anchor is the visible rectangle of the table, not its bounds. A table inside a scroll pane is as
     * wide as all of its columns, so its own width can place the popup far outside the frame.
     */
    private fun topOfTable(grid: DataGrid, columnsList: ColumnsListPopup): RelativePoint? {
      val table = grid.resultView.component
      if (!table.isShowing) return null
      val visible = table.visibleRect
      if (visible.isEmpty) return null
      val x = visible.x + ((visible.width - columnsList.component.preferredSize.width) / 2).coerceAtLeast(0)
      return RelativePoint(table, Point(x, visible.y))
    }
  }
}
