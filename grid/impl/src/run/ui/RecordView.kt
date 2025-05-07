package com.intellij.database.run.ui

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.*
import com.intellij.database.datagrid.color.ColorLayer
import com.intellij.database.datagrid.color.MutationsColorLayer
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactoryProvider
import com.intellij.database.run.ui.treetable.TreeTableResultView
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.observable.util.addMouseHoverListener
import com.intellij.openapi.observable.util.whenTextChangedFromUi
import com.intellij.openapi.util.NlsSafe
import com.intellij.profile.codeInspection.ui.addScrollPaneIfNecessary
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.hover.HoverStateListener
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.getTextWidth
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.GroupLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.LayoutFocusTraversalPolicy

class RecordView(
  private val grid: DataGrid,
  private val openValueEditorTab: () -> Unit
) : CellViewer, Disposable {
  @Volatile var isTwoColumnsLayout = false
  @Volatile var isValidPanel = true

  private val dataModel = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)

  private var panelData = PanelController(collectColumnInfos(), isTwoColumnsLayout)
  val panelDataView
    get() = panelData

  override val component: JComponent
    get() = panelData.panel
  override val preferedFocusComponent: JComponent
    get() = panelData.panel

  override fun update(event: UpdateEvent?) {
    if (event == UpdateEvent.SettingsChanged) {
      panelData = PanelController(collectColumnInfos(), isTwoColumnsLayout)
      isValidPanel = false
      return
    }

    // `false` means panelData refuses to update with this selection -> rebuild
    if (!panelData.onSelectionUpdated()) {
      panelData = PanelController(collectColumnInfos(), isTwoColumnsLayout)
      isValidPanel = false
      return
    }

    if (event == null || event == UpdateEvent.SelectionChanged) {
      return
    }

    if (event == UpdateEvent.ContentChanged) {
      panelData.updateTextFields()
      return
    }
  }

  override fun dispose() {
  }

  fun validateIfNeeded(): Boolean {
    if (isValidPanel) {
      return true
    }
    isValidPanel = true
    return false
  }

  private fun collectColumnInfos(): List<ColumnInfo> {
    return grid.visibleColumns.asIterable().mapNotNull { columnIdx ->
      val column = dataModel.getColumn(columnIdx) ?: return@mapNotNull null
      ColumnInfo(columnIdx, column.name, column)
    }
  }

  data class ColumnInfo(
    val idx: ModelIndex<GridColumn>,
    @NlsSafe val name: String,
    val column: GridColumn
  )

  data class PanelComponents(val valueField: ExtendableTextField, val name: JLabel)

  class TwoColumnPanel(uiElements: Collection<PanelComponents>) : JBPanel<TwoColumnPanel>() {
    init {
      val layout = GroupLayout(this)
      this.layout = layout

      val hGroup = layout.createParallelGroup()
      val vGroup = layout.createParallelGroup()
      val vGroupColumn1 = layout.createSequentialGroup()
      val vGroupColumn2 = layout.createSequentialGroup()

      val maxDesiredNameSize = uiElements.maxOfOrNull { (_, label) ->
        label.getTextWidth(label.text) + (label.icon?.iconWidth ?: 0) + label.iconTextGap
      } ?: 0
      val nameSize = maxDesiredNameSize.coerceIn(JBUI.scale(40) .. JBUI.scale(250))

      uiElements.forEach { (field, label) ->
        val hGroupRow = layout.createSequentialGroup()
        hGroupRow.addContainerGap()
        hGroupRow.addComponent(label, nameSize, nameSize, nameSize)
        hGroupRow.addGap(JBUIScale.scale(6))
        hGroupRow.addComponent(field, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE.toInt())
        hGroup.addGroup(hGroupRow)
        val fieldPreferredHeight = field.preferredSize.height

        vGroupColumn1.addContainerGap()
        vGroupColumn1.addComponent(label, fieldPreferredHeight, fieldPreferredHeight, fieldPreferredHeight)
        vGroupColumn1.addGap(JBUIScale.scale(10))

        vGroupColumn2.addContainerGap()
        vGroupColumn2.addComponent(field, fieldPreferredHeight, fieldPreferredHeight, fieldPreferredHeight)
        vGroupColumn2.addGap(JBUIScale.scale(10))
      }

      vGroup.addGroup(vGroupColumn1)
      vGroup.addGroup(vGroupColumn2)
      layout.setVerticalGroup(vGroup)
      layout.setHorizontalGroup(hGroup)
    }
  }

  class SingleColumnPanel(uiElements: Collection<PanelComponents>) : JBPanel<SingleColumnPanel>() {
    init {
      val layout = GroupLayout(this)
      this.layout = layout

      val hGroup = layout.createParallelGroup()
      val vGroup = layout.createParallelGroup()
      val vGroupColumn1 = layout.createSequentialGroup()

      uiElements.forEach { (field, nameLabel) ->

        val hGroupNameRow = layout.createSequentialGroup()
        hGroupNameRow.addGap(JBUIScale.scale(12))
        hGroupNameRow.addComponent(nameLabel)
        hGroupNameRow.addGap(JBUIScale.scale(12))
        hGroup.addGroup(hGroupNameRow)

        val hGroupValueRow = layout.createSequentialGroup()
        hGroupValueRow.addGap(JBUIScale.scale(9))
        hGroupValueRow.addComponent(field, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE.toInt())
        hGroupValueRow.addGap(JBUIScale.scale(9))
        hGroup.addGroup(hGroupValueRow)
        val fieldPreferredHeight = field.preferredSize.height

        vGroupColumn1.addContainerGap()
        vGroupColumn1.addComponent(nameLabel, fieldPreferredHeight, fieldPreferredHeight, fieldPreferredHeight)
        vGroupColumn1.addComponent(field, fieldPreferredHeight, fieldPreferredHeight, fieldPreferredHeight)
      }

      vGroup.addGroup(vGroupColumn1)
      layout.setVerticalGroup(vGroup)
      layout.setHorizontalGroup(hGroup)
    }
  }

  class MyTextField(
    private val openValueEditorTab: () -> Unit,
    columns: Int
  ) : ExtendableTextField(columns) {

    val originalBackground = background
    var isSelected = false

    private var hovered = false
    private val extension: ExtendableTextComponent.Extension =
      ExtendableTextComponent.Extension.create(
      AllIcons.General.ExpandComponent, AllIcons.General.ExpandComponentHover,
      DataGridBundle.message("EditMaximized.Record.to.value.editor.control")
    ) { openValueEditorTab() }

    init {
      addMouseHoverListener(null, object : HoverStateListener() {
        override fun hoverChanged(component: Component, hovered: Boolean) {
          this@MyTextField.hovered = hovered
          updateExtension()
        }
      })
    }

    fun updateExtension() {
      val shouldShow = isSelected || hovered
      if (shouldShow) {
        addExtension(extension)
      }
      else {
        removeExtension(extension)
      }
      repaint()
    }

    override fun hasFocus(): Boolean {
      /* This is a hack.
      It lies to the UI about being focused to draw light-blue border around it.
      ATM it does not have any adverse side effects.
      One should not simply rely on this method for the field */
      return isSelected
    }

    override fun getMinimumSize(): Dimension {
      return preferredSize
    }

    override fun getPreferredSize(): Dimension {
      return Dimension(JBUIScale.scale(100), super.getMinimumSize().height)
    }
  }

  inner class PanelController(columnInfos: List<ColumnInfo>, isTwoColumnLayout: Boolean) {
    val textFields: MutableMap<ModelIndex<GridColumn>, MyTextField> = mutableMapOf()
    val panel: JComponent

    private val colorLayer: ColorLayer = MutationsColorLayer(GridUtil.getDatabaseMutator(grid))
    private val textConvertors: MutableMap<ModelIndex<GridColumn>, Convertor> = mutableMapOf()

    private var rowIdx: ModelIndex<GridRow> = ModelIndex.forRow(grid, -1)
    private var selectedColumnIdx: ModelIndex<GridColumn> = ModelIndex.forColumn(grid, -1)
    private var isValidRow = false

    init {
      rowIdx = grid.selectionModel.selectedRow
      isValidRow = rowIdx.isValid(grid)
      selectedColumnIdx = grid.selectionModel.selectedColumn

      if (!isValidRow) {
        panel = JBPanelWithEmptyText()
      }
      else {
        val componentGroups = mutableListOf<PanelComponents>()
        columnInfos.forEach { columnInfo ->
          val helper = GridHelper.get(grid)
          val icon = helper.getColumnIcon(grid, columnInfo.column, true)
          val tooltip = helper.getColumnTooltipHtml(grid, columnInfo.idx)

          val field = MyTextField(openValueEditorTab, COLUMNS_SHORT).apply {
            whenTextChangedFromUi { setTextInGrid(columnInfo.idx) }

            addKeyListener(object : KeyAdapter() {
              override fun keyPressed(e: KeyEvent?) {
                if (e?.keyCode == KeyEvent.VK_ESCAPE) {
                  grid.resultView.preferredFocusedComponent.requestFocusInWindow()
                  e.consume()
                }
                if (e?.keyCode == KeyEvent.VK_ENTER) {
                  transferFocus()
                  e.consume()
                }
              }
            })

            addFocusListener(object : FocusAdapter() {
              override fun focusGained(e: FocusEvent?) {
                if (selectedColumnIdx == columnInfo.idx) {
                  return
                }
                setSelectionInGrid(columnInfo.idx)
              }
            })

            isEditable = isCellEditable(grid, rowIdx, columnInfo.idx)

            if (!isEditable) {
              toolTipText = EditorBundle.message("editing.viewer.hint")
            }
          }
          val label = JLabel(columnInfo.name).apply {
            this.icon = icon; toolTipText = tooltip
          }

          textFields[columnInfo.idx] = field
          componentGroups.add(PanelComponents(field, label))
        }
        for (kv in textFields) {
          updateConvertor(kv.key)
          updateText(kv.key)
          updateColor(kv.key)
          updateSelection(kv.key)
        }

        val innerPanel = if (isTwoColumnLayout) TwoColumnPanel(componentGroups) else SingleColumnPanel(componentGroups)
        panel = addScrollPaneIfNecessary(innerPanel.apply {
          isFocusCycleRoot = true
          isFocusTraversalPolicyProvider = true
          focusTraversalPolicy = object : LayoutFocusTraversalPolicy() {
            override fun getDefaultComponent(aContainer: Container?): Component {
              return textFields[selectedColumnIdx] ?: super.getDefaultComponent(aContainer)
            }
          }
        })
      }

    }

    fun updateColor(columnIdx: ModelIndex<GridColumn>) = textFields[columnIdx]?.let { textField ->
      textField.background = colorLayer.getCellBackground(rowIdx, columnIdx, grid, null) ?: textField.originalBackground
    }
    fun updateText(columnIdx: ModelIndex<GridColumn>) = textFields[columnIdx]?.let { textField ->
      textField.text = textConvertors[columnIdx]?.toText(dataModel.getValueAt(rowIdx, columnIdx))!!
      textField.caretPosition = 0
    }
    fun updateSelection(columnIdx: ModelIndex<GridColumn>) = textFields[columnIdx]?.let { textField ->
      val isCurrentSelected = columnIdx == selectedColumnIdx
      textField.isSelected = isCurrentSelected
      textField.updateExtension()
      if (isCurrentSelected) {
        (textField.parent as JComponent?)?.scrollRectToVisible(textField.bounds)
      }
    }
    fun updateConvertor(columnIdx: ModelIndex<GridColumn>) {
      val currentConvertor = textConvertors[columnIdx]
      if (currentConvertor == null || currentConvertor.rowIdx != rowIdx || currentConvertor.columnIdx != columnIdx) {
        textConvertors[columnIdx] = Convertor(rowIdx, columnIdx)
      }
    }
    fun setTextInGrid(columnIdx: ModelIndex<GridColumn>) = textFields[columnIdx]?.let { textField ->
      val parsed: Any? = textConvertors[columnIdx]?.fromText(textField.text)
      grid.resultView.setValueAt(
        parsed, rowIdx, columnIdx, false,
        GridRequestSource(EditMaximizedViewRequestPlace(grid, this@RecordView)).apply {
          actionCallback.doWhenProcessed {
            updateColor(columnIdx)
          }
        }
      )
    }
    fun setSelectionInGrid(columnIdx: ModelIndex<GridColumn>) = textFields[columnIdx]?.let { textField ->
      val resultView = grid.resultView
      if (resultView is TreeTableResultView) {
        resultView.tryExpand(rowIdx)
      }
      grid.selectionModel.setSelection(rowIdx, columnIdx)

      for (kv in textFields) {
        updateSelection(kv.key)
      }
      textField.requestFocusInWindow()
    }
    fun onSelectionUpdated(): Boolean {
      val oldRowIdx = rowIdx
      rowIdx = grid.selectionModel.selectedRow
      val oldSelectedColumnIdx = selectedColumnIdx
      selectedColumnIdx = grid.selectionModel.selectedColumn

      if (rowIdx.isValid(grid) != isValidRow) {
        return false
      }
      isValidRow = rowIdx.isValid(grid)

      if (oldRowIdx != rowIdx) {
        for (kv in textFields) {
          updateConvertor(kv.key)
          updateText(kv.key)
          updateColor(kv.key)
        }
      }

      if (oldSelectedColumnIdx != selectedColumnIdx) {
        for (kv in textFields) {
          updateSelection(kv.key)
        }
      }

      return true
    }
    fun updateTextFields() {
      textFields.keys.forEach { columnIdx ->
        updateText(columnIdx)
        updateColor(columnIdx)
      }
    }

    inner class Convertor(val rowIdx: ModelIndex<GridRow>, val columnIdx: ModelIndex<GridColumn>) {
      private val factory = GridCellEditorFactoryProvider.get(grid)?.getEditorFactory(grid, rowIdx, columnIdx)
      private val valueParser = factory?.getValueParser(grid, rowIdx, columnIdx)

      fun toText(value: Any?): String = factory?.getValueFormatter(grid, rowIdx, columnIdx, value)?.format()?.text
                                        ?: GridUtil.getText(grid, rowIdx, columnIdx)

      fun fromText(text: String): Any? = valueParser?.parse(text, null)
    }
  }

  companion object {

    @JvmStatic
    private fun isCellEditable(grid: DataGrid, rowIdx: ModelIndex<GridRow>, columnIdx: ModelIndex<GridColumn>): Boolean {
      if (!grid.isEditable) {
        return false
      }
      val factory = GridCellEditorFactoryProvider.get(grid)?.getEditorFactory(grid, rowIdx, columnIdx) ?: return false
      val value = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getValueAt(rowIdx, columnIdx)
      return factory.isEditableChecker.isEditable(value, grid, columnIdx)
    }
  }
}