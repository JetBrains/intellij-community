package com.intellij.database.run.ui.table

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.ide.ui.laf.darcula.DarculaTableHeaderUI
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.table.TableView
import com.intellij.ui.util.maximumHeight
import com.intellij.ui.util.width
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.table.ComponentsListFocusTraversalPolicy
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.table.*
import kotlin.math.min

class LocalFilterPopup(
  private val grid: DataGrid,
  private val resultView: TableResultView,
  private val columnIdx: ModelIndex<GridColumn>,
  private val items: List<ColumnItem>,
  columnName: @NlsSafe String
) {

  private val headerCheckbox = JCheckBox()

  private val valueColumnInfo = object : ColumnInfo<ColumnItem, Pair<String, Boolean>>(
    DataGridBundle.message("action.Console.TableResult.ColumnLocalFilter.LocalFilterPopup.ValueColumn.Name")
  ) {
    private val renderer = object : TableCellRenderer {
      private val checkboxComponent = JCheckBox()
      override fun getTableCellRendererComponent(
        table: JTable?, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
      ): Component {
        return checkboxComponent.apply {
          text = (value as Pair<*, *>).first as String
          this.isSelected = value.second as Boolean

          if (table?.isRowSelected(row) == true) {
            background = UIUtil.getTableSelectionBackground(table.hasFocus())
          }
          else {
            background = UIUtil.getTableBackground()
          }
        }
      }
    }

    override fun getCustomizedRenderer(o: ColumnItem?, renderer: TableCellRenderer?): TableCellRenderer {
      return this.renderer
    }

    override fun valueOf(item: ColumnItem?): Pair<String, Boolean>? {
      return item?.let { Pair(it.value.text, it.isSelected) }
    }
  }
  private val countColumnInfo = object : ColumnInfo<ColumnItem, Int>(
    DataGridBundle.message("action.Console.TableResult.ColumnLocalFilter.LocalFilterPopup.CountColumn.Name")
  ) {
    private val renderer = object : TableCellRenderer {
      private val labelComponent = JLabel().apply { isOpaque = true }
      override fun getTableCellRendererComponent(
        table: JTable?, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
      ): Component {
        return labelComponent
          .apply {
            text = (value as Int).toString()
            if (table?.isRowSelected(row) == true) {
              background = UIUtil.getTableSelectionBackground(table.hasFocus())
            }
            else {
              background = UIUtil.getTableBackground()
            }
          }
      }
    }

    override fun getCustomizedRenderer(o: ColumnItem?, renderer: TableCellRenderer?): TableCellRenderer {
      return this.renderer
    }

    override fun valueOf(item: ColumnItem?): Int? {
      return item?.counter
    }
  }

  private val table: TableView<ColumnItem> = TableView(ListTableModel(arrayOf(valueColumnInfo, countColumnInfo), items)).apply {
    val valueColumn = columnModel.getColumn(0)
    val countColumn = columnModel.getColumn(1)

    valueColumn.headerRenderer =
      TableCellRenderer { table, value, _, _, _, columnViewIdx ->
        headerCheckbox
          .apply {
            text = value as String
            val columnModelIdx = table?.convertColumnIndexToModel(columnViewIdx)
            val hovered = (table?.tableHeader as? HoverableTableHeader)?.hoveredColumn == columnModelIdx
            applyHover(hovered)
          }
      }
    countColumn.headerRenderer =
      object : TableCellRenderer {
        private val headerLabel = JLabel().apply {
          border = JBUI.Borders.empty(0, 2)
        }

        override fun getTableCellRendererComponent(
          table: JTable?, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, columnViewIdx: Int
        ): Component {
          return headerLabel
            .apply {
              text = value as String
              val columnModelIdx = table?.convertColumnIndexToModel(columnViewIdx)
              val hovered = (table?.tableHeader as? HoverableTableHeader)?.hoveredColumn == columnModelIdx
              applyHover(hovered)
            }
        }
      }

    adjustToContent(valueColumn, valueColumnInfo)
    adjustToContent(countColumn, countColumnInfo)

    showHorizontalLines = false
    showVerticalLines = false
    columnSelectionAllowed = false
    rowSelectionAllowed = true
  }
  private val valueColumn = table.columnModel.getColumn(0)
  private val countColumn = table.columnModel.getColumn(1)

  private val scrollPane = ScrollPaneFactory.createScrollPane(
    table,
    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  ).apply {
    border = JBUI.Borders.empty()
  }

  private val searchTextField = SearchTextField(false).apply {
    maximumHeight = height
    isFocusable = false
  }

  private val statusLabel = JLabel()

  private val popupPanel = JPanel().apply {
    layout = BorderLayout(JBUIScale.scale(5), JBUIScale.scale(5))
    border = JBUI.Borders.empty(JBInsets(5))
    add(searchTextField, BorderLayout.NORTH)
    add(scrollPane, BorderLayout.CENTER)

    updateStatusText()
    add(statusLabel, BorderLayout.SOUTH)

    isFocusCycleRoot = true
    focusTraversalPolicy = object : ComponentsListFocusTraversalPolicy() {
      override fun getOrderedComponents(): MutableList<Component> {
        return mutableListOf(searchTextField.textEditor, table)
      }
    }
  }

  val popup: JBPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(popupPanel, searchTextField.textEditor)
    .setProject(grid.project)
    .setRequestFocus(true)
    .setResizable(true)
    .setMovable(true)
    .setTitle(DataGridBundle.message("action.Console.TableResult.ColumnLocalFilter.LocalFilterPopup.Title", columnName))
    .setMinSize(Dimension(JBUIScale.scale(200), JBUIScale.scale(200)))
    .setLocateWithinScreenBounds(false)
    .createPopup().apply {
      val screenWidth = Toolkit.getDefaultToolkit().screenSize.width
      setSize(Dimension(min(screenWidth / 2, valueColumn.width + countColumn.width + popupPanel.insets.width), JBUIScale.scale(500)))
    }

  init {
    val sorter = object : TableRowSorter<TableModel>(table.model) {
      override fun toggleSortOrder(column: Int) {}
    }.apply {
      setSortable(0, false)
      setSortable(1, true)
      setComparator(1, Comparator.naturalOrder<Int>())
      rowFilter = object : RowFilter<TableModel, Int>() {
        private val textField = searchTextField
        override fun include(entry: Entry<out TableModel, out Int>?): Boolean {
          if (entry == null) {
            return false
          }

          return StringUtil.containsIgnoreCase(table.listTableModel.getItem(entry.identifier).value.text, textField.text)
        }
      }
    }

    table.rowSorter = sorter

    searchTextField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        sorter.sort()
        table.repaint()
      }
    })
    searchTextField.addKeyboardListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent?) {
        if (e?.keyCode != KeyEvent.VK_DOWN)
          return
        table.requestFocus()
        e.consume()
      }
    })

    table.tableHeader = HoverableTableHeader(table)
    table.tableHeader.putClientProperty(DarculaTableHeaderUI.SKIP_DRAWING_VERTICAL_CELL_SEPARATOR_KEY, true)
    setupFocusKeys()
    setupInputListeners(sorter)
  }

  private fun mapFilter(items: List<ColumnItem>, f: (Boolean) -> Boolean) {
    items.forEach { item ->
      item.isSelected = f(item.isSelected)
      if (item.isSelected) {
        resultView.localFilterState.enableForColumn(columnIdx, item.value)
      }
      else {
        resultView.localFilterState.disableForColumn(columnIdx, item.value)
      }
    }
    resultView.updateRowFilter()
    grid.panel.component.repaint()

    updateStatusText()
    popup.content.repaint()
  }

  private fun adjustToContent(column: TableColumn, info: ColumnInfo<ColumnItem, *>) {
    val c: Component = column.headerRenderer
      .getTableCellRendererComponent(null, column.headerValue, false, false, 0, 0)

    column.setMinWidth(c.minimumSize.width)
    val preferredWidth = c.preferredSize.width.coerceAtLeast(
      items.asSequence().map { item ->
        info.getCustomizedRenderer(item, null)
          .getTableCellRendererComponent(null, info.valueOf(item), false, false, 0, 0)
          .preferredSize.width
      }.maxOrNull() ?: 0
    ) + JBUIScale.scale(10)

    column.setPreferredWidth(preferredWidth)
    column.setWidth(preferredWidth)
  }

  private fun setupFocusKeys() {
    val inputMap = table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)

    val ksDown = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)
    val ksTab = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0)
    val actionOnDown = inputMap.get(ksDown)
    inputMap.put(ksTab, actionOnDown)

    val ksUp: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)
    val ksShiftTab = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)
    val actionOnUp = inputMap.get(ksUp)
    inputMap.put(ksShiftTab, actionOnUp)
  }

  private fun setupInputListeners(sorter: TableRowSorter<*>) {
    table.tableHeader.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        val selectedColumn = e?.let { table.columnModel.getColumn(table.columnAtPoint(it.point)) }
        if (selectedColumn == countColumn) {
          val currentSortOrder = sorter.sortKeys?.firstOrNull { x -> x.column == countColumn.modelIndex }?.sortOrder
          val newSortOrder = when (currentSortOrder) {
            SortOrder.DESCENDING -> SortOrder.UNSORTED
            SortOrder.ASCENDING -> SortOrder.DESCENDING
            else -> SortOrder.ASCENDING
          }
          sorter.sortKeys = listOf(RowSorter.SortKey(countColumn.modelIndex, newSortOrder))
        }

        if (selectedColumn != valueColumn) {
          return
        }

        headerCheckbox.isSelected = !headerCheckbox.isSelected
        val visibleItems = (0 until table.rowCount).map { table.getRow(it) }
        mapFilter(visibleItems) { _ -> headerCheckbox.isSelected }
      }

      override fun mouseExited(e: MouseEvent?) {
        (table.tableHeader as? HoverableTableHeader)?.handleMouseExit()
      }
    })
    table.tableHeader.addMouseMotionListener(object : MouseAdapter() {
      override fun mouseMoved(e: MouseEvent?) {
        (table.tableHeader as? HoverableTableHeader)?.handleMouseMove(e)
      }
    })

    table.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        val clickedRowIndex = e?.point?.let { table.rowAtPoint(it) }
        if (clickedRowIndex == -1) {
          return
        }
        val selected = clickedRowIndex?.let { table.getRow(it) }
        mapFilter(listOfNotNull(selected)) { x -> !x }
      }
    })
    table.addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent?) {
        if (e?.keyCode == KeyEvent.VK_SPACE) {
          val selected = table.selectedObject
          if (selected == null)
            return
          val isSelected = selected.isSelected
          mapFilter(table.selectedObjects) { _ -> !isSelected }
        }
      }
    })
    table.addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent?) {
        if (table.selectedObject == null) {
          table.selectionModel.addSelectionInterval(0, 0)
        }
      }
    })
  }

  private fun updateStatusText() {
    if (resultView.localFilterState.columnFilterEnabled(columnIdx)) {
      statusLabel.text = DataGridBundle.message("action.Console.TableResult.ColumnLocalFilter.LocalFilterPopup.Status.WhenSelected",
                                                grid.visibleRowsCount)
    }
    else {
      statusLabel.text = DataGridBundle.message("action.Console.TableResult.ColumnLocalFilter.LocalFilterPopup.Status.WhenEmpty")
    }
  }

  private fun JComponent.applyHover(hovered: Boolean) {
    if (hovered) {
      isOpaque = true
      background = UIUtil.getTableSelectionBackground(false)
    }
    else {
      isOpaque = false
    }
  }

  class ColumnItem(val value: LocalFilterState.Value, var isSelected: Boolean, val counter: Int)

  class HoverableTableHeader(table: JTable) : JTableHeader(table.columnModel) {
    var hoveredColumn: Int = -1
    fun handleMouseMove(e: MouseEvent?) {
      val column = e?.let { table.columnModel.getColumn(table.columnAtPoint(it.point)) }
      val oldValue = hoveredColumn
      hoveredColumn = column?.modelIndex ?: -1

      if (oldValue != hoveredColumn) {
        repaint()
      }
    }

    fun handleMouseExit() {
      val oldValue = hoveredColumn
      hoveredColumn = -1
      if (oldValue != hoveredColumn) {
        repaint()
      }
    }
  }
}
