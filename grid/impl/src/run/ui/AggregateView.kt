package com.intellij.database.run.ui

import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.extensions.ExtractorScripts
import com.intellij.database.extractors.DataAggregatorFactory
import com.intellij.database.extractors.DataExtractorFactories
import com.intellij.database.extractors.ExtractorsHelper
import com.intellij.icons.AllIcons
import com.intellij.ide.CopyProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.EditorTextFieldCellRenderer.AbbreviatingRendererComponent
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.ui.PopupHandler
import com.intellij.ui.TableCell
import com.intellij.ui.TableExpandableItemsHandler
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.table.JBTable
import com.intellij.util.IconUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.swing.*
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableModel
import kotlin.math.ceil

class AggregateView(private val grid: DataGrid) : CellViewer, Disposable {
  private val disabledAggregators: HashSet<String> = HashSet()
  private val popupGroup: ActionGroup = ActionManager.getInstance().getAction("Console.AggregateView.PopupGroup") as ActionGroup
  private val table = object : JBTable(), UiDataProvider {
    val myCopyProvider = object : CopyProvider {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
      override fun performCopy(dataContext: DataContext) {
        val selected = ArrayList<String>()
        val multipleRows = selectedRows.size > 1
        for (idx in selectedRows) {
          val cell = model.getValueAt(idx, 1) as AggregateCell
          val result = cell.future.getNow(null)?.text ?: DataGridBundle.message("status.bar.grid.aggregator.widget.calculating")
          val text: String =
            if (multipleRows) {
              "${cell.aggregator.getSimpleName()}: $result"
            }
            else {
              result
            }
          ContainerUtil.addIfNotNull(selected, text)
        }
        if (selected.size > 0) {
          val text = StringUtil.join(selected, "\n")
          CopyPasteManager.getInstance().setContents(StringSelection(text))
        }
      }

      override fun isCopyEnabled(dataContext: DataContext): Boolean {
        return selectionModel.selectedItemsCount > 0
      }

      override fun isCopyVisible(dataContext: DataContext): Boolean {
        return true
      }
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink[PlatformDataKeys.COPY_PROVIDER] = myCopyProvider
    }

    override fun getCellRenderer(row: Int, column: Int): TableCellRenderer {
      return if (column == 0) myNameRenderer
      else myResultRenderer
    }

    override fun createExpandableItemsHandler(): ExpandableItemsHandler<TableCell> {
      return object : TableExpandableItemsHandler(this) {
        override fun handleSelectionChange(selected: TableCell?, processIfUnfocused: Boolean) {
          val s = if (selected?.column == 0) selected else null // expand only first column because value column has expand button
          super.handleSelectionChange(s, processIfUnfocused)
        }
      }
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
  }

  private val tableScrollPane = JBScrollPane(table)
  private val actionToolbar = ActionManager.getInstance().createActionToolbar(
    "TP",
    ActionManager.getInstance().getAction("Console.TableResult.AggregatorViewGroup") as ActionGroup,
    true
  )
  private val mainPanel = panel {
    row {
      cell(tableScrollPane).align(AlignX.FILL + AlignY.TOP).resizableColumn()
    }
    row {
      cell(actionToolbar.component)
    }
  }

  init {
    actionToolbar.targetComponent = mainPanel
  }

  private val myResultRenderer: AggregateViewCellRenderer = AggregateViewCellRenderer()
  private val myNameRenderer: AggregatorNameCellRenderer = AggregatorNameCellRenderer()

  override val component: JComponent
    get() = mainPanel
  override val preferedFocusComponent: JComponent
    get() = mainPanel

  init {
    disabledAggregators.addAll(GridUtil.getSettings(grid)?.getDisabledAggregators() ?: emptyList())
    cleanOldDisabledScripts()

    val expandAction = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        changeDisplayForAllAggregators(true)
      }
    }
    val collapseAction = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        changeDisplayForAllAggregators(false)
      }
    }

    val aggregatorDirPath = ExtractorScripts.getAggregatorScriptsDirectory()?.absolutePath
    table.setShowGrid(false)
    table.setShowColumns(false)
    tableScrollPane.horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
    expandAction.registerCustomShortcutSet(CustomShortcutSet(KeyboardShortcut.fromString("RIGHT")), table)
    collapseAction.registerCustomShortcutSet(CustomShortcutSet(KeyboardShortcut.fromString("LEFT")), table)
    UiNotifyConnector.installOn(tableScrollPane, object : Activatable {
      override fun showNotify() {
        FileDocumentManager.getInstance().saveDocuments { document ->
          val file = FileDocumentManager.getInstance().getFile(document)
          if (aggregatorDirPath != null && file != null) {
            file.path.startsWith(aggregatorDirPath) && FileDocumentManager.getInstance().isDocumentUnsaved(document)
          }
          else {
            false
          }
        }
      }
    })

    grid.project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        val aggregatorsEvents = events.filter { event -> aggregatorDirPath != null && event.path.startsWith(aggregatorDirPath) }
        if (aggregatorsEvents.isNotEmpty()) {
          ApplicationManager.getApplication().invokeLater {
            update()
          }
        }
      }
    })
    Disposer.register(this, myResultRenderer)

    val mouseListener: MouseListener = object : MouseAdapter() {
      private var isNewPress = true

      override fun mouseReleased(e: MouseEvent?) {
        isNewPress = true
      }

      override fun mousePressed(mouseEvent: MouseEvent) {
        val table = mouseEvent.source as JTable
        val index = table.rowAtPoint(mouseEvent.point)
        if (mouseEvent.mouseButton == MouseButton.Right) {
          if (index >= 0 && isNewPress && !table.selectionModel.isSelectedIndex(index)) {
            table.selectionModel.clearSelection()
            table.selectionModel.addSelectionInterval(index, index)
          }
          isNewPress = false
          return
        }
        if (!UIUtil.isActionClick(mouseEvent, MouseEvent.MOUSE_PRESSED)) {
          isNewPress = false
          return
        }
        if (index >= 0) {
          if (isNewPress) {
            isNewPress = false
            val currentElement = table.model.getValueAt(index, 1)
            if (currentElement == null) {
              return
            }
            val panel = myResultRenderer.getTableCellRendererComponent(table, currentElement, false, false, index, 1)
            val result = (currentElement as AggregateCell).future.getNow(null)?.processMouseClickEvent(mouseEvent,
                                                                                                       table.getCellRect(index, 1, false),
                                                                                                       panel) ?: true
            if (result && !(table.selectionModel.isSelectedIndex(index) && !table.hasFocus())) {
              if ((mouseEvent.modifiersEx and (InputEvent.CTRL_DOWN_MASK or InputEvent.META_DOWN_MASK)) == 0) {
                table.selectionModel.clearSelection()
              }
              table.selectionModel.addSelectionInterval(index, index)
            }
          }
          else {
            table.selectionModel.addSelectionInterval(index, index)
          }
          table.requestFocus()
          table.updateUI()
        }
        else {
          isNewPress = false
        }
      }
    }

    table.addMouseListener(mouseListener)

    table.addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        if (popupGroup != ActionGroup.EMPTY_GROUP) {
          ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_POPUP, popupGroup).component.show(comp, x, y)
        }
      }
    })
  }

  fun changeDisplayForAllAggregators(isExpand: Boolean) {
    for (rowId in table.selectedRows) {
      val element = table.model.getValueAt(rowId, 1) as AggregateCell
      element.future.getNow(null)?.setDecorateState(isExpand)
    }
    table.requestFocus()
    table.updateUI()
  }

  fun getEnabledAggregatorsScripts(): List<String> {
    val aggregators = mutableListOf<Aggregator>()
    for (idx in 0 until table.model.rowCount) {
      aggregators.add((table.model.getValueAt(idx, 1) as AggregateCell).aggregator)
    }
    return aggregators.asSequence().filter { aggregator ->
      !disabledAggregators.contains(aggregator.getName())
    }.map { aggregator -> aggregator.getName() }.toList()
  }

  fun getDisabledAggregatorsScripts(): List<String> {
    return disabledAggregators.toList()
  }

  fun setAggregatorSelection(name: String, selected: Boolean) {
    if (selected) {
      disabledAggregators.remove(name)
    }
    else {
      disabledAggregators.add(name)
    }
  }

  override fun dispose() {
  }

  override fun update(event: UpdateEvent?) {
    val model = object : DefaultTableModel() {
      override fun isCellEditable(row: Int, column: Int): Boolean {
        return false
      }
    }
    model.addColumn("name")
    model.addColumn("value")
    if (grid.selectionModel.selectedColumnCount == 0 || grid.selectionModel.selectedRowCount == 0) {
      table.model = model
      return
    }
    val oldAggregatorCells = mutableListOf<AggregateCell>()
    for (idx in 0 until table.model.rowCount) {
      oldAggregatorCells.add((table.model.getValueAt(idx, 1) as AggregateCell))
    }
    val scripts: List<DataAggregatorFactory> = DataExtractorFactories.getAggregatorScripts(ExtractorsHelper.getInstance(grid), GridUtil::suggestPlugin)
    val aggregators: LinkedList<Aggregator> = LinkedList()
    scripts.forEach { script ->
      val findResult = oldAggregatorCells.find { cell -> cell.aggregator.getName() == script.name }
      if (findResult == null) {
        val config = ExtractorsHelper.getInstance(grid).createExtractorConfig(grid, grid.objectFormatter)
        script.createAggregator(config)?.let {
          aggregators.add(Aggregator(grid, it, script.simpleName, script.name))
        }
      }
      else {
        aggregators.add(findResult.aggregator)
      }
    }
    aggregators.sortBy { sc -> sc.getSimpleName() }
    var idxCounter = 0
    val selectedList = mutableListOf<Int>()
    for (aggregator in aggregators) {
      if (disabledAggregators.contains(aggregator.getName())) continue
      val future = aggregator.update()
      val idx = oldAggregatorCells.indexOfFirst { cell -> cell.aggregator == aggregator }
      val cell = if (idx == -1) null else oldAggregatorCells[idx]
      future.thenAccept { result ->
        result.setFullTextShown(cell?.future?.getNow(null)?.isFullTextShown() ?: false)
      }
      if (idx != -1 && table.selectionModel.isSelectedIndex(idx)) selectedList.add(idx)
      model.addRow(arrayOf(aggregator.getSimpleName(), AggregateCell(aggregator, future)))
      idxCounter++
    }
    table.model = model
    table.getColumn(table.model.getColumnName(0)).minWidth = 120
    table.getColumn(table.model.getColumnName(0)).maxWidth = 120
    for (index in selectedList) {
      table.selectionModel.addSelectionInterval(index, index)
    }
    table.updateUI()
  }

  private fun cleanOldDisabledScripts() {
    val scriptNames: List<String> = DataExtractorFactories.getAggregatorScripts(ExtractorsHelper.getInstance(grid), null).map { script -> script.name }
    disabledAggregators.removeIf { name -> !scriptNames.contains(name) }
  }

  fun getCurrentModel(): TableModel {
    return table.model
  }

  class AggregateViewCellRenderer : TableCellRenderer, Disposable {
    private val myPanel: AggregatorViewPanel = AggregatorViewPanel()

    private fun getTextColor(isThrowable: Boolean): Color? {
      return if (isThrowable) NamedColorUtil.getInactiveTextColor() else UIUtil.getActiveTextColor()
    }

    override fun getTableCellRendererComponent(table: JTable,
                                               value: Any?,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): AggregatorViewPanel {
      val future = (value as AggregateCell).future
      val result = future.getNow(null)
      val cellHasFocus = isSelected && table.hasFocus()
      val textColor = if (isSelected) NamedColorUtil.getListSelectionForeground(cellHasFocus)
      else getTextColor(result?.isScriptExceptionHappened() ?: false)
      val t = TableHoverListener.getHoveredRow(table)
      val selectionColor: Color
      if (t == row && !isSelected) {
        selectionColor = JBUI.CurrentTheme.Table.Hover.background(true)
      }
      else {
        selectionColor = if (isSelected) UIUtil.getListSelectionBackground(cellHasFocus) else UIUtil.getListBackground()
      }
      myPanel.setComponentsForeground(textColor)
      myPanel.setComponentsBackground(selectionColor)
      val text = result?.text ?: DataGridBundle.message("status.bar.grid.aggregator.widget.calculating")
      myPanel.setText(text)
      myPanel.setMultilineState(text.contains("\n"), result?.isFullTextShown() ?: true, isSelected, cellHasFocus)
      val rowHeight = (result?.getRowsCount() ?: 1) * myPanel.getRowHeight() + table.rowMargin + myPanel.getBordersHeight()
      if (table.getRowHeight(row) != rowHeight) {
        table.setRowHeight(row, rowHeight)
      }
      return myPanel
    }

    override fun dispose() {
      Disposer.dispose(myPanel)
    }
  }

  class AggregatorNameCellRenderer : TableCellRenderer {
    private val myPanel: AggregatorNamePanel = AggregatorNamePanel()

    override fun getTableCellRendererComponent(table: JTable,
                                               value: Any?,
                                               isSelected: Boolean,
                                               hasFocus: Boolean,
                                               row: Int,
                                               column: Int): AggregatorNamePanel {
      val cellHasFocus = isSelected && table.hasFocus()
      val selectionColor = if (isSelected) UIUtil.getListSelectionBackground(cellHasFocus) else UIUtil.getListBackground()
      val nameColor = if (isSelected) NamedColorUtil.getListSelectionForeground(cellHasFocus) else UIUtil.getActiveTextColor()
      myPanel.setComponentsForeground(nameColor)
      myPanel.setComponentsBackground(selectionColor)
      myPanel.setText(value as String)
      return myPanel
    }
  }

  class AggregatorViewPanel : JPanel(BorderLayout()), Disposable {
    private val myResultPanel: JPanel = JPanel(BorderLayout())
    private val myIcon: JBLabel = JBLabel()
    private val myArrowIcon: ArrowIcon = ArrowIcon()
    private val myTextPanel = AbbreviatingRendererComponent(null, null, true, true, true)
    private val colorsScheme: AggregateViewPanelColorsScheme

    init {
      myIcon.verticalAlignment = JLabel.NORTH
      myResultPanel.add(myTextPanel, BorderLayout.CENTER)
      myResultPanel.add(myIcon, BorderLayout.EAST)
      myResultPanel.isOpaque = false
      myTextPanel.isOpaque = false
      myTextPanel.components.forEach { c -> (c as JComponent).isOpaque = false }
      border = JBUI.Borders.empty(5, 5, 5, 10)
      add(myResultPanel)

      colorsScheme = AggregateViewPanelColorsScheme(myTextPanel.editor.colorsScheme)
      myTextPanel.editor.colorsScheme = colorsScheme
    }

    fun isClickOnButton(e: MouseEvent, rectangle: Rectangle): Boolean {
      if (myIcon.icon == null) return false
      val leftBorder = rectangle.x + border.getBorderInsets(this).left + myResultPanel.width - myIcon.width
      val rightBorder = leftBorder + myIcon.width
      val topBorder = rectangle.y + border.getBorderInsets(this).top
      val downBorder = topBorder + myIcon.height
      return (e.x > leftBorder && e.y > topBorder && e.x < rightBorder && e.y < downBorder)
    }

    fun getRowHeight(): Int {
      return ceil((getFontMetrics(font).height * colorsScheme.lineSpacing).toDouble()).toInt()
    }

    fun getBordersHeight(): Int {
      return border.getBorderInsets(this).top + border.getBorderInsets(this).bottom
    }

    fun setComponentsForeground(textColor: Color?) {
      colorsScheme.foreground = textColor
    }

    fun setComponentsBackground(color: Color) {
      background = color
      myTextPanel.background = color
      myResultPanel.background = color
    }

    fun getComponentBackground(): Color {
      return myTextPanel.background
    }

    fun setText(text: String) {
      myTextPanel.setText(text)
    }

    fun setMultilineState(isMultiline: Boolean, showFullText: Boolean, isSelected: Boolean, hasFocus: Boolean) {
      if (isMultiline) {
        myIcon.icon = myArrowIcon
        myArrowIcon.showFullText = showFullText
        myIcon.border = JBUI.Borders.empty(if (showFullText) 5 else 3, 2, 0, 0)
        myArrowIcon.isSelected = isSelected
        myArrowIcon.hasFocus = hasFocus
        myIcon.isVisible = true
        myTextPanel.setForceSingleLine(!showFullText)
        myTextPanel.setAppendEllipsis(!showFullText)
      }
      else {
        myIcon.icon = null
        myIcon.isVisible = false
        myTextPanel.setForceSingleLine(true)
        myTextPanel.setAppendEllipsis(true)
      }
    }

    override fun dispose() {
      Disposer.dispose(myTextPanel)
    }
  }

  private class ArrowIcon : Icon {
    var showFullText: Boolean = false
    var isSelected: Boolean = false
    var hasFocus: Boolean = false

    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
      val cx = x + this.iconWidth / 2.0
      val cy = y + this.iconHeight / 2.0
      val angle = if (showFullText) -(Math.PI / 2) else Math.PI / 2
      val gg = g?.create() as Graphics2D
      gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      gg.rotate(angle, cx, cy)
      UIUtil.drawImage(gg, IconUtil.toImage(if (isSelected && hasFocus) AllIcons.Icons.Ide.MenuArrowSelected else AllIcons.Icons.Ide.MenuArrow),
                       x, y, c)
      gg.dispose()
    }

    override fun getIconWidth(): Int {
      return AllIcons.Icons.Ide.MenuArrow.iconWidth
    }

    override fun getIconHeight(): Int {
      return AllIcons.Icons.Ide.MenuArrow.iconHeight
    }
  }

  class AggregatorNamePanel : JPanel(BorderLayout()) {
    private val myNameLabel: JLabel = JLabel()

    init {
      myNameLabel.border = JBUI.Borders.empty(2, 0)
      border = JBUI.Borders.empty(5, 5, 5, 10)
      add(myNameLabel, BorderLayout.NORTH)
    }

    fun setComponentsForeground(textColor: Color?) {
      myNameLabel.foreground = textColor
      myNameLabel.updateUI()
    }

    fun setComponentsBackground(color: Color) {
      background = color
      myNameLabel.background = color
    }

    fun setText(@NlsSafe text: String) {
      myNameLabel.text = text
    }
  }
}

private class AggregateViewPanelColorsScheme(colorsScheme: EditorColorsScheme) : DelegateColorScheme(colorsScheme) {
  var foreground: Color? = null

  override fun getDefaultForeground(): Color {
    return foreground ?: super.getDefaultForeground()
  }
}

class AggregateCell(val aggregator: Aggregator, val future: CompletableFuture<AggregationResult>)