package com.intellij.database.run.ui

import com.intellij.database.DataGridBundle
import com.intellij.database.datagrid.ActualGridCellRequest
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridCellRequest
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridHelper
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.actual
import com.intellij.database.datagrid.color.ColorLayer
import com.intellij.database.datagrid.color.MutationsColorLayer
import com.intellij.database.datagrid.overrideValue
import com.intellij.database.datagrid.request
import com.intellij.database.remote.jdbc.LobInfo
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactoryProvider
import com.intellij.database.run.ui.treetable.TreeTableResultView
import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.observable.util.addMouseHoverListener
import com.intellij.openapi.observable.util.whenTextChangedFromUi
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.profile.codeInspection.ui.addScrollPaneIfNecessary
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.COLUMNS_SHORT
import com.intellij.ui.hover.HoverStateListener
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.getTextWidth
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.GroupLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.LayoutFocusTraversalPolicy

class RecordView(
  private val grid: DataGrid,
  private val openValueEditorTab: () -> Unit
) : CellViewer, Disposable {
  @Volatile
  internal var isTwoColumnsLayout = false
  @Volatile
  internal var isValidPanel = true

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

  private class GridEditState(val gridEditable: Boolean, reason: @NlsContexts.Tooltip String?) {
    val readOnlyTooltip: @NlsContexts.Tooltip String =
      if (reason.isNullOrEmpty()) EditorBundle.message("editing.viewer.hint") else reason
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
        val namePreferredHeight = nameLabel.preferredSize.height
        val fieldPreferredHeight = field.preferredSize.height

        vGroupColumn1.addContainerGap()
        vGroupColumn1.addComponent(nameLabel, namePreferredHeight, namePreferredHeight, namePreferredHeight)
        vGroupColumn1.addComponent(field, fieldPreferredHeight, fieldPreferredHeight, fieldPreferredHeight)
        vGroupColumn1.addGap(JBUIScale.scale(10))
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

    /** Shows a preview instead of the value: a text component measures its whole content on every caret move. */
    var isTruncated: Boolean = false
      private set

    private val extension: ExtendableTextComponent.Extension =
      ExtendableTextComponent.Extension.create(
      AllIcons.General.ExpandComponent, AllIcons.General.ExpandComponentHover,
      DataGridBundle.message("EditMaximized.Record.to.value.editor.control")
    ) { openValueEditorTab() }

    private val truncatedExtension: ExtendableTextComponent.Extension =
      ExtendableTextComponent.Extension.create(
      AllIcons.General.ExpandComponent, AllIcons.General.ExpandComponentHover,
      TRUNCATED_MESSAGE, true
    ) { openValueEditorTab() }

    /** Shows [value], as a preview when it is too large for a text component to handle or [valueTruncated] already. */
    fun setValue(
      value: String,
      editable: Boolean,
      readOnlyTooltip: @NlsContexts.Tooltip String?,
      valueTruncated: Boolean = false,
    ) {
      val tooLong = value.length > MAX_INLINE_VALUE_LENGTH
      isTruncated = tooLong || valueTruncated
      text = if (tooLong) value.preview() + Typography.ellipsis else value
      isEditable = editable && !isTruncated
      // A preview wins over the read-only reason: it is the hint one can act on, and it says why the field is locked.
      val tooltip = if (isTruncated) TRUNCATED_MESSAGE else if (!editable) readOnlyTooltip else null
      // The read-only reason is already HTML, so escaping it would print its markup.
      setToolTipText(tooltip?.let(HtmlChunk::raw))
      getAccessibleContext().accessibleDescription = tooltip
      caretPosition = 0
      updateExtension()
    }

    /** Cuts at [MAX_INLINE_VALUE_LENGTH], never between the halves of a surrogate pair. */
    private fun String.preview(): String {
      val end = if (this[MAX_INLINE_VALUE_LENGTH - 1].isHighSurrogate()) MAX_INLINE_VALUE_LENGTH - 1 else MAX_INLINE_VALUE_LENGTH
      return substring(0, end)
    }

    init {
      addMouseHoverListener(null, object : HoverStateListener() {
        override fun hoverChanged(component: Component, hovered: Boolean) {
          this@MyTextField.hovered = hovered
          updateExtension()
        }
      })
    }

    fun updateExtension() {
      removeExtension(extension)
      removeExtension(truncatedExtension)
      removeExtension(READ_ONLY_EXTENSION)
      // A truncated value always advertises the value editor: it is the only place the whole value can be seen.
      if (isTruncated) {
        addExtension(READ_ONLY_EXTENSION)
        addExtension(truncatedExtension)
      }
      else if (isSelected || hovered) {
        addExtension(extension)
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

    private companion object {
      val TRUNCATED_MESSAGE: @NlsContexts.Tooltip String = DataGridBundle.message("EditMaximized.Record.value.truncated")

      /** Marks a field as showing a preview it cannot edit, next to the text rather than only in a tooltip. */
      val READ_ONLY_EXTENSION: ExtendableTextComponent.Extension = object : ExtendableTextComponent.Extension {
        override fun getIcon(hovered: Boolean): Icon = AllIcons.Ide.Readonly
        override fun isIconBeforeText(): Boolean = true
        override fun getTooltip(): String = TRUNCATED_MESSAGE
        override fun getIconGap(): Int = JBUIScale.scale(3)
      }
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

          val field = MyTextField({
            if (selectedColumnIdx != columnInfo.idx) setSelectionInGrid(columnInfo.idx)
            openValueEditorTab()
          }, COLUMNS_SHORT).apply {
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

          }
          val label = JLabel(columnInfo.name).apply {
            this.icon = icon; toolTipText = tooltip
          }

          textFields[columnInfo.idx] = field
          componentGroups.add(PanelComponents(field, label))
        }
        val editState = gridEditState()
        for (kv in textFields) {
          updateConvertor(kv.key)
          updateText(kv.key, editState)
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

    private fun updateColor(columnIdx: ModelIndex<GridColumn>) = textFields[columnIdx]?.let { textField ->
      textField.background = colorLayer.getCellBackground(rowIdx, columnIdx, grid, null) ?: textField.originalBackground
    }
    private fun updateText(columnIdx: ModelIndex<GridColumn>, editState: GridEditState) = textFields[columnIdx]?.let { textField ->
      val rawValue = dataModel.getValueAt(rowIdx, columnIdx)
      val value = textConvertors[columnIdx]?.toText(rawValue)!!
      val editable = editState.gridEditable && isCellEditable(grid.request(rowIdx, columnIdx))
      // Only a prefix of the value was loaded, so it is a preview however short it came out. The table view marks it too.
      val valueTruncated = (rawValue as? LobInfo<*>)?.isTruncated == true
      textField.setValue(value, editable, if (editable) null else editState.readOnlyTooltip, valueTruncated)
    }

    /** Scanning the edit guards is too costly to repeat for every column, so the grid-wide answer is taken once. */
    private fun gridEditState(): GridEditState {
      val gridEditable = grid.isEditable
      return GridEditState(gridEditable, if (gridEditable) null else GridEditGuard.get(grid)?.getReasonText(grid))
    }
    fun updateSelection(columnIdx: ModelIndex<GridColumn>) = textFields[columnIdx]?.let { textField ->
      val isCurrentSelected = columnIdx == selectedColumnIdx
      textField.isSelected = isCurrentSelected
      textField.updateExtension()
      if (isCurrentSelected) {
        (textField.parent as JComponent?)?.scrollRectToVisible(textField.bounds)
      }
    }
    private fun updateConvertor(columnIdx: ModelIndex<GridColumn>) {
      val currentConvertor = textConvertors[columnIdx]
      if (currentConvertor == null || currentConvertor.request.rowIdx != rowIdx || currentConvertor.request.columnIdx != columnIdx) {
        textConvertors[columnIdx] = Convertor(grid.request(rowIdx, columnIdx))
      }
    }
    fun setTextInGrid(columnIdx: ModelIndex<GridColumn>) = textFields[columnIdx]?.let { textField ->
      // The field holds a preview of the value, not the value: writing it back would replace the value with the preview.
      if (textField.isTruncated) return@let
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
    private fun setSelectionInGrid(columnIdx: ModelIndex<GridColumn>) = textFields[columnIdx]?.let { textField ->
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
        val editState = gridEditState()
        for (kv in textFields) {
          updateConvertor(kv.key)
          updateText(kv.key, editState)
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
      val editState = gridEditState()
      textFields.keys.forEach { columnIdx ->
        updateText(columnIdx, editState)
        updateColor(columnIdx)
      }
    }

    inner class Convertor(request: GridCellRequest<GridRow, GridColumn>) {
      val request: ActualGridCellRequest<GridRow, GridColumn> = request.actual()
      private val factory = GridCellEditorFactoryProvider.provideEditorFactory(request)
      private val valueParser = factory?.getValueParser(request)

      fun toText(value: Any?): String = factory?.getValueFormatter(request.overrideValue(value))?.format()?.text
                                        ?: GridUtil.getText(request.grid as DataGrid, request.rowIdx, request.columnIdx)

      fun fromText(text: String): Any? = valueParser?.parse(text, null)
    }
  }

  companion object {
    /** Longest value shown in a record field; beyond it the value editor is the only place to see and edit it. */
    private const val MAX_INLINE_VALUE_LENGTH = 10_000

    /** Cell-level editability only; the grid-wide answer comes from [PanelController.gridEditState]. */
    @JvmStatic
    private fun isCellEditable(request: GridCellRequest<GridRow, GridColumn>): Boolean {
      val factory = GridCellEditorFactoryProvider.provideEditorFactory(request) ?: return false
      return factory.isEditableChecker.isEditable(request.getValue(), request.grid, request.columnIdx)
    }
  }
}
