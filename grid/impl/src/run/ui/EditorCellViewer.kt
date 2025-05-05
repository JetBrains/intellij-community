package com.intellij.database.run.ui

import com.intellij.database.datagrid.*
import com.intellij.database.extractors.DisplayType
import com.intellij.database.run.ReservedCellValue
import com.intellij.database.run.ui.CellViewer.Companion.CELL_VIEWER_KEY
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactory
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactory.DefaultValueToText
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactoryProvider
import com.intellij.database.run.ui.grid.renderers.DefaultTextRendererFactory
import com.intellij.ide.highlighter.HighlighterFactory
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.FileContentUtilCore
import com.intellij.util.LineSeparator
import javax.swing.JComponent
import kotlin.math.max
import kotlin.math.min

/**
 * @author Liudmila Kornilova
 **/
class EditorCellViewer(private val project: Project,
                       private val grid: DataGrid,
                       editable: Boolean) : CellViewer {
  private val editor: EditorEx = createEditor(editable)
  private val formattedModeHandler = FormattedModeHandler(grid, editor.document, project, this::disableUpdateListener)
  private val valueParserCache = ValueParserCache(grid)
  private val updateDocumentListener = object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      documentChanged()
    }
  }

  var isSoftWraps: Boolean
    get() = editor.settings.isUseSoftWraps
    set(value) {
      PropertiesComponent.getInstance().setValue(SOFT_WRAPS_DISABLED, !value)
      editor.settings.isUseSoftWraps = value
    }
  val isFormattedModeSupported: Boolean
    get() = formattedModeHandler.supportsCurrentValue()
  var isFormattedMode: Boolean
    get() = formattedModeHandler.isEnabled()
    set(value) {
      if (value == formattedModeHandler.isEnabled()) return
      formattedModeHandler.setEnabled(value)
      if (value) formattedModeHandler.reformat()
      else updateText()
    }

  var offset: Int
    get() = editor.caretModel.offset
    set(value) {
      if (value >= 0 && value <= editor.document.textLength) {
        editor.caretModel.moveToOffset(value)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
      }
    }

  private val wrappedComponent = UiDataProvider.wrapComponent(editor.component) { sink ->
    sink[CommonDataKeys.EDITOR] = editor
  }

  override val component: JComponent
    get() = wrappedComponent
  override val preferedFocusComponent: JComponent
    get() = editor.contentComponent

  init {
    editor.document.addDocumentListener(updateDocumentListener)

    DataGridCellTypeListener.addDataGridListener(grid, { rows, columns ->
      if (rows.asIterable().any { it == valueParserCache.row } && columns.asIterable().any { it == valueParserCache.column }) {
        valueParserCache.clearCache()
      }
    }, this)

    grid.addDataGridListener(object : DataGridListener {
      override fun onCellDisplayTypeChanged(columnIdx: ModelIndex<GridColumn>, type: DisplayType) {
        super.onCellDisplayTypeChanged(columnIdx, type)
      }

      override fun onContentChanged(dataGrid: DataGrid?, place: GridRequestSource.RequestPlace?) {
        valueParserCache.clearCache()
      }
    }, this)
  }

  private fun disableUpdateListener(action: () -> Unit) {
    editor.document.removeDocumentListener(updateDocumentListener)
    action()
    editor.document.addDocumentListener(updateDocumentListener)
  }

  override fun dispose() {
    if (!editor.isDisposed) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
  }

  override fun update(event: UpdateEvent?) {
    if (event is UpdateEvent.ValueChanged) {
      editor.selectionModel.removeSelection()
      val columnIdx = grid.selectionModel.leadSelectionColumn
      val rowIdx = grid.selectionModel.leadSelectionRow
      updateText(event.value, rowIdx, columnIdx)
      return
    }

    editor.selectionModel.removeSelection()
    updateText()
  }

  private fun createEditor(editable: Boolean): EditorEx {
    val virtualFile = LightVirtualFile("Value Editor", PlainTextLanguage.INSTANCE, "")
    val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                   ?: EditorFactory.getInstance().createDocument("")
    val editor = if (editable) EditorFactory.getInstance().createEditor(document, project) as EditorEx
    else EditorFactory.getInstance().createViewer(document, project) as EditorEx

    editor.scrollPane.border = null
    editor.setShowPlaceholderWhenFocused(true)
    editor.contextMenuGroupId = "Console.TableResult.CellEditor.Popup"
    editor.settings.isUseSoftWraps = !PropertiesComponent.getInstance().getBoolean(SOFT_WRAPS_DISABLED)
    editor.settings.isLineNumbersShown = false
    editor.putUserData(CELL_VIEWER_KEY, this)
    return editor
  }

  private fun updateText() {
    val rowIdx = grid.selectionModel.leadSelectionRow
    val columnIdx = grid.selectionModel.leadSelectionColumn
    if (!rowIdx.isValid(grid) || !columnIdx.isValid(grid)) return
    val model = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
    val value = model.getValueAt(rowIdx, columnIdx)
    updateText(value, rowIdx, columnIdx)
  }

  private fun updateText(value: Any?, rowIdx: ModelIndex<GridRow>, columnIdx: ModelIndex<GridColumn>) {
    setText(value ?: ReservedCellValue.NULL, rowIdx, columnIdx)
    val language = DefaultTextRendererFactory.getLanguage(grid, rowIdx, columnIdx)
    val fragment = GridHelper.get(grid).createCellCodeFragment(editor.document.text, project, grid, rowIdx, columnIdx)
    updateLanguage(language, fragment)
  }

  private fun updateLanguage(language: Language, fragment: PsiCodeFragment?) {
    WriteAction.runAndWait<Nothing> {
      val virtualFile = FileDocumentManager.getInstance().getFile(editor.document) as? LightVirtualFile ?: return@runAndWait
      if (virtualFile.language != language) {
        virtualFile.language = language
        val highlighter = HighlighterFactory.createHighlighter(project, virtualFile)
        editor.highlighter = highlighter
      }
      if (fragment != null) {
        GridUtilCore.associatePsiSafe(editor.document, fragment)
      }
      else {
        FileContentUtilCore.reparseFiles(virtualFile)
      }
      formattedModeHandler.reformat()
    }
  }

  fun select(start: Int, end: Int) {
    val len = editor.document.textLength
    editor.selectionModel.setSelection(min(len, max(0, start)), min(len, end))
  }

  fun selectAll() {
    editor.selectionModel.setSelection(0, editor.document.textLength)
  }

  private fun setText(value: Any, rowIdx: ModelIndex<GridRow>, columnIdx: ModelIndex<GridColumn>) {
    val document = editor.document
    disableUpdateListener {
      ApplicationManager.getApplication().runWriteAction {
        val offsetBefore = offset
        val formatter = GridCellEditorFactoryProvider.get(grid)
                          ?.getEditorFactory(grid, rowIdx, columnIdx)
                          ?.getValueFormatter(grid, rowIdx, columnIdx, value)
                        ?: DefaultValueToText(grid, columnIdx, value)
        val result = formatter.format()
        val file = FileDocumentManager.getInstance().getFile(document)
        file?.charset = result.charset
        file?.bom = result.bom
        setText(document, result.text)
        offset = min(offsetBefore, document.textLength)
      }
      if (value is ReservedCellValue) {
        editor.setPlaceholder(value.displayName)
      }
      else editor.setPlaceholder(null)
    }
  }

  private fun documentChanged() {
    editor.setPlaceholder(null)
    val row = grid.selectionModel.leadSelectionRow
    val column = grid.selectionModel.leadSelectionColumn
    if (!row.isValid(grid) || !column.isValid(grid)) return
    val parser = valueParserCache.getValueParser(row, column)
    val document = editor.document
    PsiDocumentManager.getInstance(project).commitDocument(document)
    val file = PsiDocumentManager.getInstance(project).getPsiFile(document)
    val text = if (file != null) getText(document, formattedModeHandler.minimize(file)) else getText(document, document.text)
    val value = parser.parse(text, document)
    grid.resultView.setValueAt(value, row, column, false, GridRequestSource(EditMaximizedViewRequestPlace(grid, this)))
  }

  companion object {
    private const val SOFT_WRAPS_DISABLED = "EditMaximizedView.SOFT_WRAPS_DISABLED"
    private val LINE_SEPARATOR_KEY: Key<LineSeparator?> = Key.create("EDIT_MAXIMIZED_ETF_LINE_SEPARATOR")

    private fun getText(document: Document, text: String): String {
      val separator: LineSeparator? = LINE_SEPARATOR_KEY.get(document)
      return if (separator == null) text
      else StringUtil.convertLineSeparators(text, separator.separatorString)
    }

    private fun setText(document: Document, text: String) {
      var separator: LineSeparator? = LINE_SEPARATOR_KEY.get(document)
      if (separator == null) {
        separator = StringUtil.detectSeparators(text)
      }
      LINE_SEPARATOR_KEY.set(document, separator)
      document.setText(StringUtil.convertLineSeparators(text))
    }
  }
}

object EditorCellViewerFactory : CellViewerFactory {
  override fun getSuitability(grid: DataGrid, row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>): Suitability {
    return if (row.isValid(grid) && column.isValid(grid)) Suitability.MIN_1 else Suitability.NONE
  }

  override fun createViewer(grid: DataGrid): CellViewer {
    return EditorCellViewer(grid.project, grid, true)
  }
}

object ReadonlyEditorCellViewerFactory : CellViewerFactory {
  override fun getSuitability(grid: DataGrid, row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>): Suitability {
    if (!row.isValid(grid) || !column.isValid(grid)) return Suitability.NONE
    if (!grid.isEditable) return Suitability.MIN_2
    val factory = GridCellEditorFactoryProvider.get(grid)?.getEditorFactory(grid, row, column) ?: return Suitability.NONE

    val value = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getValueAt(row, column)
    val isEditable = factory.isEditableChecker.isEditable(value, grid, column)
    return if (isEditable) Suitability.MIN_1
    else Suitability.MIN_2
  }

  override fun createViewer(grid: DataGrid): CellViewer {
    return EditorCellViewer(grid.project, grid, false)
  }
}


class ValueParserCache(private val grid: DataGrid) {
  var row: ModelIndex<GridRow> = ModelIndex.forRow(grid, -1)
  var column: ModelIndex<GridColumn> = ModelIndex.forColumn(grid, -1)
  private val defaultParser = GridCellEditorFactory.ValueParser { text, _ -> text }
  private var currentParser: GridCellEditorFactory.ValueParser = defaultParser

  fun getValueParser(row: ModelIndex<GridRow>, column: ModelIndex<GridColumn>): GridCellEditorFactory.ValueParser {
    if (this.row != row || this.column != column) {
      this.row = row
      this.column = column
      val factory = GridCellEditorFactoryProvider.get(grid)?.getEditorFactory(grid, row, column)
      currentParser = factory?.getValueParser(grid, row, column) ?: defaultParser
    }
    return currentParser
  }

  fun clearCache() {
    this.row = ModelIndex.forRow(grid, -1)
    this.column = ModelIndex.forColumn(grid, -1)
  }
}