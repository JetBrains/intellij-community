package com.intellij.database.run.ui.columns

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ModelIndexSet
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.GridColumnPinning
import com.intellij.database.run.ui.table.ColumnPinning
import com.intellij.ide.setToolTipText
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ClientProperty
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBList
import com.intellij.ui.components.SearchFieldWithExtension
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import javax.swing.event.DocumentEvent

/**
 * The column list popup. It searches the columns of [grid], and it hides or shows them.
 *
 * Every change applies at once, so the popup has no OK button and no Cancel button.
 */
@ApiStatus.Internal
class ColumnsListPopup(private val grid: DataGrid) {
  private val commands get() = controller.commands
  private val model get() = controller.model

  private val listModel = CollectionListModel<Row>()

  /**
   * A document grid is left out for now. Only [com.intellij.database.datagrid.DocumentDataHookUp] implements
   * a column move in the data, so its header drag writes the file, and the popup must not disagree with the header.
   * Every other grid reorders its view, which is what its header drag does too.
   */
  private val reorderable: Boolean = GridUtil.getDocumentDataHookUp(grid) == null

  private val renderer = ItemRenderer(ColumnPinning.isEnabled(), reorderable)


  /** The spacer row that holds the line between the pinned columns and the rest, or -1 when there is none. */
  private var separatorRow = -1

  private val list = object : JBList<Row>(listModel), UiDataProvider {
    override fun uiDataSnapshot(sink: DataSink) {
      sink[PlatformDataKeys.COPY_PROVIDER] = actions.copyProvider
    }

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      drag?.paintBand(g)
      paintPinnedGroupLine(g)
      drag?.paintLine(g)
    }

    /** Explains a pin that cannot be taken. A disabled control has no other way to say why. */
    override fun getToolTipText(event: MouseEvent): String? {
      val row = locationToIndex(event.point)
      if (row < 0 || getCellBounds(row, row)?.contains(event.point) != true) return null
      val item = itemAt(row) ?: return null
      if (item.canTogglePin || controlAt(event.x, row) != RowControl.PIN) return null
      val column = item.modelIndex ?: return null
      return if (item.pinned) commands.reasonUnpinRefuses()
      else commands.reasonPinRefuses(ModelIndexSet.forColumns(grid, column.asInteger()))
    }
  }.apply {
    selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
    setExpandableItemsEnabled(false)
    cellRenderer = renderer
    accessibleContext.accessibleName = DataGridBundle.message("action.Console.TableResult.ColumnsList.Popup.AccessibleName")
    // The list UI fills a selected row with a square rectangle before the renderer draws. The row paints
    // its own fill in the shape the theme asks for, so that one is made invisible rather than fought.
    ClientProperty.put(this, RenderingUtil.CUSTOM_SELECTION_BACKGROUND, Supplier { UIUtil.TRANSPARENT_COLOR })
    emptyText.text = DataGridBundle.message("action.Console.TableResult.ColumnsList.Popup.NoMatch")
    emptyText.appendSecondaryText(
      DataGridBundle.message("action.Console.TableResult.ColumnsList.Popup.ClearSearch"),
      SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
    ) { searchField.text = "" }
  }

  private val searchField = SearchTextField(false).apply {
    val placeholder = DataGridBundle.message("action.Console.TableResult.ColumnsList.Popup.SearchPlaceholder")
    textEditor.emptyText.text = placeholder
    textEditor.accessibleContext.accessibleName = placeholder
  }

  /**
   * How many columns the filter keeps, hidden ones included, because that is what a bulk action acts on.
   * It is not the number a bulk action would change, which is smaller when some columns already agree.
   */
  private val matchCount = JLabel().apply { foreground = UIUtil.getContextHelpForeground() }

  /** The filter with its count. SearchTextField holds a plain text field, which takes no extension itself. */
  private val searchRow = SearchFieldWithExtension(matchCount, searchField).apply {
    // The inset goes on after the wrapper is built, because its constructor clears the border of the
    // component it takes. The count keeps the clear button of the field on its left, as the find toolbar
    // keeps its own count after that button.
    matchCount.border = JBUI.Borders.emptyRight(MATCH_COUNT_RIGHT_PAD)
  }

  private val counter = JLabel().apply { foreground = UIUtil.getContextHelpForeground() }

  private val showAllLink = ActionLink("") { applyVisibility(model.matched, true) }
    .apply { horizontalAlignment = SwingConstants.RIGHT }

  private val hideAllLink = ActionLink("") { applyVisibility(model.matched, false) }
    .apply { horizontalAlignment = SwingConstants.RIGHT }

  private val content: JComponent = object : BorderLayoutPanel(), UiDataProvider {
    override fun uiDataSnapshot(sink: DataSink) = actions.uiDataSnapshot(sink)
  }.apply {
    border = JBUI.Borders.empty(POPUP_PAD)
    preferredSize = Dimension(JBUIScale.scale(DEFAULT_WIDTH), JBUIScale.scale(DEFAULT_HEIGHT))
    addToTop(searchRow)
    addToCenter(ScrollPaneFactory.createScrollPane(
      list,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED,
    ).apply { border = JBUI.Borders.empty(6, 0) })
    addToBottom(BorderLayoutPanel().apply {
      addToCenter(counter)
      // HorizontalLayout puts the gap between the links and none beside them. A BorderLayout keeps its
      // gap next to the west child, so the link that stays alone there sits off the right edge.
      addToRight(JPanel(HorizontalLayout(LINK_GAP)).apply {
        isOpaque = false
        border = JBUI.Borders.emptyRight(LINKS_RIGHT_PAD)
        add(showAllLink)
        add(hideAllLink)
      })
    })
  }

  /** The component of the popup, which a test drives without showing the popup. */
  val component: JComponent get() = content

  val popup: JBPopup = JBPopupFactory.getInstance()
    .createComponentPopupBuilder(content, searchField.textEditor)
    .setProject(grid.project)
    // The title is also the handle that moves the popup. Without one AbstractPopup builds a caption of
    // zero height, and the move listener it installs there has nothing for the user to grab.
    .setTitle(DataGridBundle.message("action.Console.TableResult.ColumnsList.Popup.Title"))
    // A popup is a modal context by default, and then the key dispatcher runs only an action that says
    // it works in one. Go to DDL and the other platform actions do not, so the popup must not be modal.
    .setModalContext(false)
    .setRequestFocus(true)
    .setResizable(true)
    .setMovable(true)
    .setDimensionServiceKey(grid.project, DIMENSION_KEY, false)
    .setMinSize(Dimension(JBUIScale.scale(260), JBUIScale.scale(200)))
    .createPopup()

  /** The drag, or null in a document grid, where a reorder would have to write the file. */
  private var drag: ColumnsListDrag? = null
  private val scope = grid.coroutineScope.childScope("ColumnsListPopup")
  private val controller = ColumnsListController(grid, popup, scope, ::updateRows)
  private val actions = ColumnsListActions(
    grid, controller, ::selectedColumns, ::copyTargets,
    canMoveRow = { targetOfMove(it) != null },
    moveRow = { moveSelectedRow(it) },
  )
  private var previousLead = -1
  private var restoringSelection = false
  private var pointerInsideList = false

  init {
    Disposer.register(grid, popup)
    Disposer.register(popup) { scope.cancel() }
    searchField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) = search()
    })
    installKeys()
    installMouse()
    installHover()
    installContextMenu()
    val toolTipManager = ToolTipManager.sharedInstance()
    toolTipManager.registerComponent(list)
    Disposer.register(popup) {
      if (pointerInsideList && toolTipManager in list.mouseMotionListeners) {
        toolTipManager.mousePressed(MouseEvent(list, MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false))
      }
      list.removeMouseMotionListener(toolTipManager)
      toolTipManager.unregisterComponent(list)
    }
    list.addListSelectionListener { event ->
      if (restoringSelection || event.valueIsAdjusting) return@addListSelectionListener
      skipSeparatorSelection()
      previousLead = list.leadSelectionIndex
      selectColumnsInGrid()
    }
    drag = if (reorderable) ColumnsListDrag(this, list, listModel, renderer).also { it.install() } else null
    controller.start()
    restoringSelection = true
    val selected = grid.selectionModel.selectedColumns.asIterable().toSet()
    listModel.items.forEachIndexed { index, row ->
      if (row is Row.Item && row.value.modelIndex in selected) list.addSelectionInterval(index, index)
    }
    restoringSelection = false
    previousLead = list.leadSelectionIndex
  }

  /**
   * Tracks the row under the pointer, rather than install [com.intellij.ui.hover.ListHoverListener].
   * `WideSelectionListUI` paints a hover behind whichever row that listener reports, before the row has
   * any say, and the separator must react to nothing.
   */
  private fun installHover() {
    val listener = object : MouseAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        pointerInsideList = true
        setHoveredRow(hoverableRowAt(e.point))
      }

      override fun mouseEntered(e: MouseEvent) {
        pointerInsideList = true
        setHoveredRow(hoverableRowAt(e.point))
      }

      override fun mouseExited(e: MouseEvent) {
        pointerInsideList = false
        setHoveredRow(-1)
      }
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
    if (separatorRow < 0 || !list.isSelectedIndex(separatorRow)) return
    restoringSelection = true
    try {
      val lead = list.leadSelectionIndex
      val single = list.selectedIndices.size == 1
      list.removeSelectionInterval(separatorRow, separatorRow)
      if (lead == separatorRow) {
        val next = separatorRow + if (previousLead > separatorRow) -1 else 1
        if (next in 0 until listModel.size) {
          if (single) list.selectedIndex = next else list.addSelectionInterval(next, next)
        }
      }
    }
    finally {
      restoringSelection = false
    }
  }

  private fun search() = controller.search(searchField.text)

  private fun ensureCurrentColumns(): Boolean = controller.ensureCurrentColumns()

  private fun updateRows(preserveSelection: Boolean = true) {
    restoringSelection = true
    val selected = if (preserveSelection) {
      list.selectedValuesList.filterIsInstance<Row.Item>().mapTo(HashSet()) { it.value.modelIndex }
    }
    else emptySet()
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
      if (row is Row.Item && row.value.modelIndex != null && row.value.modelIndex in selected) list.addSelectionInterval(index, index)
    }
    restoringSelection = false
    previousLead = list.leadSelectionIndex
    counter.text = DataGridBundle.message(
      "action.Console.TableResult.ColumnsList.Popup.Counter", model.shownCount, model.totalCount
    )
    // The footer counts the grid and the label beside the filter counts the filter. The words alone no
    // longer say which is which, so the whole sentence goes on the tooltip and on the description too.
    val explanation = DataGridBundle.message(
      "action.Console.TableResult.ColumnsList.Popup.CounterTooltip", model.shownCount, model.totalCount
    )
    counter.setToolTipText(HtmlChunk.text(explanation))
    counter.accessibleContext.accessibleDescription = explanation
    val searching = !model.filter.isEmpty
    updateMatchCount(searching)
    showAllLink.text = DataGridBundle.message(
      if (searching) "action.Console.TableResult.ColumnsList.Popup.ShowMatching"
      else "action.Console.TableResult.ColumnsList.Popup.ShowAll"
    )
    hideAllLink.text = DataGridBundle.message(
      if (searching) "action.Console.TableResult.ColumnsList.Popup.HideMatching"
      else "action.Console.TableResult.ColumnsList.Popup.HideAll"
    )
    // A link that cannot act leaves the row rather than greying out. The one that can act then always
    // sits at the same distance from the right edge, instead of moving by the width of its neighbour.
    showAllLink.isVisible = model.matched.any { !it.visible }
    hideAllLink.isVisible = model.matched.any { it.visible }
    showAllLink.isEnabled = showAllLink.isVisible
    hideAllLink.isEnabled = hideAllLink.isVisible
    alignBulkLinks()
  }

  /**
   * Gives both bulk links one width and right aligned text, so the last letter of the live one lands in
   * the same place each time the pair swaps roles.
   */
  private fun alignBulkLinks() {
    showAllLink.preferredSize = null
    hideAllLink.preferredSize = null
    val width = maxOf(showAllLink.preferredSize.width, hideAllLink.preferredSize.width)
    showAllLink.preferredSize = Dimension(width, showAllLink.preferredSize.height)
    hideAllLink.preferredSize = Dimension(width, hideAllLink.preferredSize.height)
  }

  /**
   * Shows the count of the filter. It also goes on the description of the field, because a label that
   * changes while the user types is announced to nobody.
   */
  private fun updateMatchCount(searching: Boolean) {
    val matches = model.matched.size
    matchCount.text =
      if (searching) DataGridBundle.message("action.Console.TableResult.ColumnsList.Popup.MatchCount", matches) else ""
    matchCount.isVisible = searching
    searchField.textEditor.accessibleContext.accessibleDescription =
      if (searching) DataGridBundle.message("action.Console.TableResult.ColumnsList.Popup.SearchAccessibleDescription", matches)
      else null
  }

  /** The row that carries the separator above it, which is the first row after the pinned group. */
  private fun firstRowAfterPinned(): Int {
    val last = model.rows.indexOfLast { it.pinned }
    return if (last in 0 until model.rows.lastIndex) last + 1 else -1
  }

  private fun applyVisibility(items: List<ColumnsListItem>, visible: Boolean) =
    controller.applyVisibility(items.mapNotNull { it.modelIndex }, visible)

  /** The column of the list row at [index], or null for the separator row. */
  internal fun itemAt(index: Int): ColumnsListItem? =
    (listModel.items.getOrNull(index) as? Row.Item)?.value

  private fun toggleSelected() {
    val selected = list.selectedValuesList.filterIsInstance<Row.Item>().map { it.value }.filter { it.isColumn }
    if (selected.isEmpty()) return
    applyVisibility(selected, !selected.first().visible)
  }

  /** Whether the grid permits the move between the two list rows. */
  fun canMoveRow(from: Int, to: Int): Boolean {
    val column = itemAt(from)?.modelIndex ?: return false
    val target = itemAt(to)?.modelIndex ?: return false
    return controller.canMove(column, target)
  }

  /** Translates a row gesture into a move of one column. */
  fun moveRow(from: Int, to: Int) {
    val column = itemAt(from)?.modelIndex ?: return
    val target = itemAt(to)?.modelIndex ?: return
    controller.move(column, target, before = from > to)
  }

  private fun togglePin(item: ColumnsListItem) {
    item.modelIndex?.let(controller::togglePin)
  }

  /** Selects the columns of the selected rows in the table, then closes. A double click and Enter do this. */
  fun selectColumnsInGridAndClose() {
    if (!ensureCurrentColumns()) return
    if (list.isSelectionEmpty) {
      val first = listModel.items.indexOfFirst { it is Row.Item && it.value.isColumn }
      if (first >= 0) list.selectedIndex = first
    }
    val selected = list.selectedValuesList.filterIsInstance<Row.Item>().mapNotNull { it.value.modelIndex }
    if (controller.revealColumns(selected)) popup.cancel()
  }

  fun selectColumnsInGrid() {
    val columns = list.selectedValuesList.filterIsInstance<Row.Item>().mapNotNull { it.value.modelIndex }
    controller.selectColumns(columns)
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

  fun copyNames(): Unit = actions.copyNames()

  fun copyNamesAndTypes(): Unit = actions.copyNamesAndTypes()

  /** Whether the grid shows its columns in the order the data has. */
  fun isOriginalOrder(): Boolean = controller.isOriginalOrder()

  /** Restores the data order while preserving visibility and pins. */
  fun restoreOriginalOrder(): Unit = controller.restoreOriginalOrder()

  fun hasPinnedColumns(): Boolean =
    ColumnPinning.isEnabled() && (grid as? GridColumnPinning)?.hasPinnedColumns() == true

  fun unpinAllColumns(): Unit = controller.unpinAll()

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
        if (row < 0 || list.getCellBounds(row, row)?.contains(e.point) != true ||
            itemAt(row) == null || list.isSelectedIndex(row)) return
        list.selectedIndex = row
      }
    })
    PopupHandler.installPopupMenu(list, contextMenuGroup(), ACTION_PLACE)
  }

  fun contextMenuGroup(): ActionGroup =
    CustomActionsSchema.getInstance().getCorrectedAction(COLUMNS_LIST_POPUP_GROUP) as ActionGroup

  /**
   * The row the selected one would land on when it moves by [delta], or null when it cannot move.
   *
   * A step of one crosses the separator, which is a row of the list but no place for a column, so the
   * step goes one further in that case.
   */
  private fun targetOfMove(delta: Int): Int? {
    val from = list.selectedIndex
    if (from < 0 || list.selectedIndices.size != 1) return null
    var to = from + delta
    if (to in 0 until listModel.size && itemAt(to) == null) to += delta
    return if (canMoveRow(from, to)) to else null
  }

  /** Moves the selected row by [delta] and keeps it selected. Returns false when it cannot move. */
  fun moveSelectedRow(delta: Int): Boolean {
    val from = list.selectedIndex
    val to = targetOfMove(delta) ?: return false
    moveRow(from, to)
    return true
  }

  private fun installKeys() {
    list.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), TOGGLE_ACTION)
    list.actionMap.put(TOGGLE_ACTION, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) = toggleSelected()
    })
    if (reorderable) {
      actions.moveRowAction(-1).registerCustomShortcutSet(CommonShortcuts.MOVE_UP, list, popup)
      actions.moveRowAction(1).registerCustomShortcutSet(CommonShortcuts.MOVE_DOWN, list, popup)
    }
    list.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), NAVIGATE_ACTION)
    list.actionMap.put(NAVIGATE_ACTION, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        selectColumnsInGridAndClose()
      }
    })
    searchField.textEditor.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), NAVIGATE_ACTION)
    searchField.textEditor.actionMap.put(NAVIGATE_ACTION, list.actionMap.get(NAVIGATE_ACTION))
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
        if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger) return
        val row = list.locationToIndex(e.point)
        if (row < 0 || list.getCellBounds(row, row)?.contains(e.point) != true) return
        val item = itemAt(row) ?: return
        when {
          onCheckBox(e, row, item) -> if (item.isColumn && e.clickCount == 1) applyVisibility(listOf(item), !item.visible)
          controlAt(e.x, row) == RowControl.PIN -> if (item.isColumn && e.clickCount == 1) togglePin(item)
          // The grip reads as a control, so a plain click on it moves nothing.
          controlAt(e.x, row) == RowControl.GRIP -> Unit
          // A single click has already reached the table through the selection listener.
          item.isColumn && e.clickCount >= 2 -> selectColumnsInGridAndClose()
          item.isColumn -> Unit
        }
      }
    })
  }

  /** The control under [x], which both sit at the right end of the row. */
  private fun controlAt(x: Int, row: Int): RowControl? {
    val bounds = list.getCellBounds(row, row) ?: return null
    val gripEnd = bounds.x + bounds.width - JBUIScale.scale(CONTROLS_RIGHT_PAD)
    val gripStart = gripEnd - gripWidth
    val pinStart = gripStart - pinWidth - JBUIScale.scale(CONTROL_GAP)
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

  /** A row of the list. The separator is a row of its own, so it joins no hover and no selection. */
  sealed interface Row {
    class Item(val value: ColumnsListItem) : Row

    object Separator : Row
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

  /** Where a dragged row lands: at the bottom edge of [row] when [below], and at its top edge otherwise. */
  data class DropLine(val row: Int, val below: Boolean)

  /**
   * What a drag carries.
   *
   * The column and not the row, because the list can rebuild between the grab and the drop, and then the
   * row would name another column. The list too, so that a drag from somewhere else cannot land here.
   * `RowsDnDSupport` compares its own component in the same way.
   */
  private class DraggedColumn(val list: JList<*>, val index: ModelIndex<GridColumn>, val column: GridColumn)

  /** What a drag from [row] carries, or null when that row cannot travel. */
  fun dragPayload(row: Int): Any? {
    if (!controller.hasCurrentColumns()) return null
    val column = (listModel.items.getOrNull(row) as? Row.Item)?.value?.modelIndex ?: return null
    val value = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(column) ?: return null
    return DraggedColumn(list, column, value)
  }

  /** The row [payload] names now, or null when the drag came from elsewhere or its column has gone. */
  fun rowOfPayload(payload: Any?): Int? {
    val dragged = payload as? DraggedColumn ?: return null
    if (dragged.list !== list) return null
    if (grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(dragged.index) !== dragged.column) return null
    val row = listModel.items.indexOfFirst { it is Row.Item && it.value.modelIndex == dragged.index }
    return if (row >= 0) row else null
  }

  /** The rows a drag from [row] may land in, which are the rows of its own group. */
  fun dropBandRows(row: Int): List<Int> = drag?.bandRows(row) ?: emptyList()

  /** Where a drag from [from] to [to] lands, or null when the list refuses the drop. */
  fun dropLineAt(from: Int, to: Int): DropLine? = drag?.lineAt(from, to)

  /** A control at the right end of a row. */
  private enum class RowControl { PIN, GRIP }

  companion object {
    private const val TOGGLE_ACTION = "columnsListToggle"
    private const val NAVIGATE_ACTION = "columnsListNavigate"
    private const val ACTION_PLACE = "ColumnsListPopup"
    private const val FOCUS_LIST_ACTION = "columnsListFocusList"
    private const val DIMENSION_KEY = "ColumnsListPopup"
    private const val POPUP_PAD = 10

    /** The trailing inset that lines the match count up with the links below it. */
    private const val LINKS_RIGHT_PAD = 2

    /** The count sits inside the search field, which holds its own border, so it needs the wider inset. */
    private const val MATCH_COUNT_RIGHT_PAD = 4

    /** The gap between the two bulk links, which the layout scales itself. */
    private const val LINK_GAP = 12
    private const val DEFAULT_WIDTH = 420
    private const val DEFAULT_HEIGHT = 480




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
