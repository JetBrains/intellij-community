package com.intellij.database.run.ui

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.DataGridCellTypeListener
import com.intellij.database.datagrid.DataGridListener
import com.intellij.database.datagrid.GridCellRequest
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridHelper
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.GridUtilCore
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.overrideValue
import com.intellij.database.datagrid.selectedCellRequest
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
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
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
 * Default text-based [CellViewer] implementation used by the Value Editor tab.
 *
 * The "Editor" part of the name refers to the backing editor component ([EditorEx]).
 */
open class EditorCellViewer(
  protected val project: Project,
  protected val grid: DataGrid,
  editable: Boolean,
) : CellViewer {
  protected val editor: EditorEx = createEditor(editable)
  private var lastUpdatedCell: Pair<ModelIndex<GridRow>, ModelIndex<GridColumn>>? = null
  private val formattedModeHandler = FormattedModeHandler(grid, editor.document, project, this::disableUpdateListener)
  private val valueParserCache = ValueParserCache()
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
    sink[PlatformCoreDataKeys.FILE_EDITOR] = TextEditorProvider.getInstance().getTextEditor(editor)
  }

  override val component: JComponent
    get() = wrappedComponent
  override val preferedFocusComponent: JComponent
    get() = editor.contentComponent

  init {
    editor.document.addDocumentListener(updateDocumentListener)

    val reason = GridEditGuard.get(grid)?.getReasonText(grid)
    if (reason != null && reason.isNotEmpty()) {
      EditorModificationUtil.setReadOnlyHint(editor, reason)
    }

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

  /** Updates editor text without triggering the [documentChanged] listener. */
  protected fun updateEditorTextSilently(text: String) {
    disableUpdateListener {
      getApplication().runWriteAction {
        editor.document.setText(text)
        offset = editor.document.textLength
      }
    }
  }

  /** Clears the cached value parser (e.g., after language/mode change). */
  protected fun clearValueParserCache() {
    valueParserCache.clearCache()
  }

  protected fun getLastUpdatedCell(): Pair<ModelIndex<GridRow>, ModelIndex<GridColumn>>? {
    return lastUpdatedCell
  }

  override fun dispose() {
    editor.document.removeDocumentListener(updateDocumentListener)
    if (!editor.isDisposed) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
  }

  override fun update(event: UpdateEvent?) {
    if (event is UpdateEvent.ValueChanged) {
      editor.selectionModel.removeSelection()
      updateText(grid.selectedCellRequest().overrideValue(event.value))
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
    val request = grid.selectedCellRequest()
    if (!request.isValid()) return
    updateText(request)
  }

  private fun updateText(request: GridCellRequest<GridRow, GridColumn>) {
    lastUpdatedCell = request.rowIdx to request.columnIdx
    val request2 = request.takeIf { it.getValue() != null } ?: request.overrideValue(ReservedCellValue.NULL)
    setText(request2)
    val (language, fragment) = resolveLanguageAndFragment(request)
    updateLanguage(language, fragment)
  }

  protected open fun resolveLanguageAndFragment(
    request: GridCellRequest<GridRow, GridColumn>
  ): Pair<Language, PsiCodeFragment?> {
    val language = DefaultTextRendererFactory.getLanguage(request)
    val fragment = GridHelper.get(grid).createCellCodeFragment(editor.document.text, project, request)
    return language to fragment
  }

  protected fun updateLanguage(language: Language, fragment: PsiCodeFragment?) {
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

  private fun setText(request: GridCellRequest<GridRow, GridColumn>) {
    val document = editor.document
    val value = request.getValue()
    disableUpdateListener {
      getApplication().runWriteAction {
        val offsetBefore = offset
        val formatter = GridCellEditorFactoryProvider.provideEditorFactory(request)
                          ?.getValueFormatter(request)
                        ?: DefaultValueToText(grid, request.columnIdx, value)
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
    val request = grid.selectedCellRequest()
    if (!request.isValid()) return
    val document = editor.document
    PsiDocumentManager.getInstance(project).commitDocument(document)
    val file = PsiDocumentManager.getInstance(project).getPsiFile(document)
    val text = if (file != null) getText(document, formattedModeHandler.minimize(file)) else getText(document, document.text)
    val value = parseDocumentValue(request, text, document)
    grid.resultView.setValueAt(
      value,
      request.rowIdx, request.columnIdx,
      false,
      GridRequestSource(EditMaximizedViewRequestPlace(grid, this))
    )
  }

  protected open fun parseDocumentValue(
    request: GridCellRequest<GridRow, GridColumn>,
    text: String,
    document: Document,
  ): Any? {
    return valueParserCache.getValueParser(request).parse(text, document)
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
  override fun getSuitability(request: GridCellRequest<GridRow, GridColumn>): Suitability {
    return if (request.isValid()) Suitability.MIN_1 else Suitability.NONE
  }

  override fun createViewer(grid: DataGrid): CellViewer {
    return EditorCellViewer(grid.project, grid, true)
  }
}

object ReadonlyEditorCellViewerFactory : CellViewerFactory {
  override fun getSuitability(request: GridCellRequest<GridRow, GridColumn>): Suitability {
    if (!request.isValid()) return Suitability.NONE
    if (!request.grid.isEditable) return Suitability.MIN_2
    val factory = GridCellEditorFactoryProvider.provideEditorFactory(request) ?: return Suitability.NONE

    val isEditable = factory.isEditableChecker.isEditable(request.getValue(), request.grid, request.columnIdx)
    return if (isEditable) Suitability.MIN_1
    else Suitability.MIN_2
  }

  override fun createViewer(grid: DataGrid): CellViewer {
    return EditorCellViewer(grid.project, grid, false)
  }
}


class ValueParserCache() {
  var row: ModelIndex<GridRow>? = null
  var column: ModelIndex<GridColumn>? = null
  private val defaultParser = GridCellEditorFactory.ValueParser { text, _ -> text }
  private var currentParser: GridCellEditorFactory.ValueParser = defaultParser

  fun getValueParser(request: GridCellRequest<GridRow, GridColumn>): GridCellEditorFactory.ValueParser {
    if (this.row != request.rowIdx || this.column != request.columnIdx) {
      this.row = request.rowIdx
      this.column = request.columnIdx
      val factory = GridCellEditorFactoryProvider.provideEditorFactory(request)
      currentParser = factory?.getValueParser(request) ?: defaultParser
    }
    return currentParser
  }

  fun clearCache() {
    this.row = null
    this.column = null
  }
}
