package com.intellij.database.datagrid

import com.intellij.database.DataGridBundle
import com.intellij.database.GridScopeProvider
import com.intellij.database.connection.throwable.info.SimpleErrorInfo
import com.intellij.database.csv.CsvFormat
import com.intellij.database.datagrid.DocumentDataHookUp.DataMarkup.BIG_INTEGER_MERGER
import com.intellij.database.datagrid.DocumentDataHookUp.DataMarkup.BOOLEAN_MERGER
import com.intellij.database.datagrid.DocumentDataHookUp.DataMarkup.DOUBLE_MERGER
import com.intellij.database.datagrid.DocumentDataHookUp.DataMarkup.INTEGER_MERGER
import com.intellij.database.datagrid.DocumentDataHookUp.DataMarkup.STRING_MERGER
import com.intellij.database.datagrid.DocumentDataHookUp.DataMarkup.getClassName
import com.intellij.database.datagrid.DocumentDataHookUp.DataMarkup.getType
import com.intellij.database.datagrid.GridUtilCore.isPageSizeUnlimited
import com.intellij.database.dbimport.CsvImportUtil
import com.intellij.database.dbimport.TypeMerger
import com.intellij.database.remote.dbimport.ErrorRecord
import com.intellij.database.run.ui.grid.GridMutationModel
import com.intellij.database.run.ui.grid.GridStorageAndModelUpdater
import com.intellij.database.run.ui.grid.editors.GridCellEditorHelper
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset


/**
 * Read-only CSV hookup that parses the file as a stream instead of loading it into a [com.intellij.openapi.editor.Document].
 *
 * Unlike [CsvDocumentDataHookUp] it honours [GridPagingModel.getPageSize] and is bounded by no preview budget, so it
 * can back a long-lived source that is read more than once. It stays read-only - there is no mutator - but it is
 * wired through a [GridMutationModel] like every other hookup, because that is what carries the model events a grid
 * attached to it subscribes to.
 *
 * [format] has to be the effective format: `headerRecord != null` means "the first row is a header" and `rowNumbers`
 * means "the first column is a row label". The parser strips the row-label column itself, so it reaches neither the
 * columns nor the rows of this hookup.
 *
 * Column names are whatever the file says. Blank and duplicated header cells are reported verbatim, because a grid
 * attached to this hookup has to show the header that needs fixing; whoever resolves columns by name repairs them
 * above this class.
 */
class CsvStreamGridDataHookUp(
  project: Project,
  private val file: VirtualFile,
  private val format: CsvFormat,
  private val charset: Charset,
  private val batchChars: Int = DEFAULT_BATCH_CHARS,
) : GridDataHookUpBase<GridRow, GridColumn>(project), DisposableGridDataHookUp, HookUpVirtualFileProvider {

  private val model: DataGridListModel = DataGridListModel(GridCellEditorHelper::areValuesEqual)
  private val mutationModel: GridMutationModel = GridMutationModel(this)
  private val pagingModel: MultiPageModel<GridRow, GridColumn> = MultiPageModelImpl(mutationModel, null)
  private val modelUpdater: GridStorageAndModelUpdater = GridStorageAndModelUpdater(model, mutationModel, null)
  private val hookUpScope = service<GridScopeProvider>().cs.childScope(javaClass.name)
  private val loader: GridLoader = CsvStreamLoader()

  val csvFormat: CsvFormat get() = format

  val firstRowIsHeader: Boolean get() = format.headerRecord != null

  /**
   * Whether the first column holds row labels rather than data, which the parser strips off every record.
   *
   * [CsvFormat.rowNumbers] is the legacy name of this one option: the CSV format settings bind their
   * "First column is header" checkbox straight to it, and the import layer converts in both directions -
   * `getEffectiveFormat()` passes `firstColumnIsHeader` into that slot.
   */
  val firstColumnIsHeader: Boolean get() = format.rowNumbers

  override fun getDataModel(): GridModel<GridRow, GridColumn> = model

  override fun getMutationModel(): GridModel<GridRow, GridColumn> = mutationModel

  override fun getPageModel(): GridPagingModel<GridRow, GridColumn> = pagingModel

  override fun getLoader(): GridLoader = loader

  override fun getVirtualFile(): VirtualFile = file

  override fun dispose() {
    hookUpScope.cancel()
  }

  /** One parsed page. [columns] is `null` on a continuation page, which keeps the schema the model already has. */
  private class CsvPage(
    val columns: List<GridColumn>?,
    val rows: List<GridRow>,
    val errors: List<ErrorRecord>,
  )

  private inner class CsvStreamLoader : GridLoaderBase(this@CsvStreamGridDataHookUp, pagingModel, modelUpdater) {

    /**
     * [offset] is the number of data records to skip, 0-based; the header record is not a data row. Row `i` of the
     * page becomes `Row.create(offset + i, ...)`, i.e. `rowNum == offset + i + 1`, which is what [loadingStarted]
     * pre-sets and what makes [GridLoaderBase.loadNextPage] and [GridLoaderBase.loadPreviousPage] land on the right
     * record.
     *
     * Reading a page other than the first re-parses the file from its start and skips [offset] records, because the
     * parser is sequential. That is the same cost the scripted hookup already pays.
     */
    override fun load(source: GridRequestSource, offset: Int) {
      if (offset < 0) {
        // A negative offset addresses records from the end of the file, which is what loadLastPage asks for.
        // This hookUp does not support total row count, as it means scanning the full file.
        failRequest(source, DataGridBundle.message("csv.stream.source.last.page.unsupported"))
        return
      }
      if (!hookUpScope.isActive) {
        failRequest(source, DataGridBundle.message("csv.stream.source.released"))
        return
      }
      hookUpScope.launch(ModalityState.defaultModalityState().asContextElement()) {
        try {
          val page = withContext(Dispatchers.Default) { readPage(offset) }
          withContext(Dispatchers.EDT) { publishPage(page, offset, source) }
        }
        catch (e: CancellationException) {
          notifyRequestFinished(source, false)
          throw e
        }
        catch (e: Throwable) {
          notifyRequestError(source, SimpleErrorInfo.create(e))
          notifyRequestFinished(source, false)
        }
      }
    }

    /** Parses from the beginning of the file, skips [offset] records and collects at most one page of them. */
    private fun readPage(offset: Int): CsvPage {
      val reader = openCsvReader()
      try {
        val collector = PageCollector(offset, pageModel.pageSize)
        collector.collectFrom(StreamCsvFormatParser(format, batchChars, reader))
        return CsvPage(
          columns = resolvePageColumns(offset, collector),
          rows = buildRows(offset, collector.records),
          errors = collector.errors,
        )
      }
      finally {
        reader.close()
      }
    }

    private fun openCsvReader(): CsvReader {
      val stream = CharsetToolkit.inputStreamSkippingBOM(file.inputStream.buffered())
      return CsvReader(BufferedReader(InputStreamReader(stream, charset)))
    }

    /**
     * The schema is established by a first-page load and then reused. A continuation page must not redefine it: its
     * own values would infer different types and, for a format without a header record, even a different column
     * count. `null` therefore means "keep the columns the model already has".
     */
    private fun resolvePageColumns(offset: Int, collector: PageCollector): List<GridColumn>? =
      if (offset == 0 || model.columnCount == 0) buildColumns(collector.header, collector.records) else null

    /**
     * Names come from the header record as parsed, and are generated when the format has no header record. Types are
     * inferred from the loaded records the same way [CsvDocumentDataHookUp] does it — without that every column
     * degrades to `TEXT` downstream.
     */
    private fun buildColumns(header: Array<CsvToken>?, records: List<Array<CsvToken>>): List<GridColumn> {
      val names = resolveColumnNames(header, countColumns(header, records))
      return names.mapIndexed { index, name ->
        val merger = determineColumnType(records, index)
        DataConsumer.Column(index, name, getType(merger), merger.name, getClassName(merger))
      }
    }

    private fun countColumns(header: Array<CsvToken>?, records: List<Array<CsvToken>>): Int =
      header?.size ?: records.maxOfOrNull { it.size } ?: 0

    /**
     * The names the file itself carries: a header cell is reported as parsed, so a blank stays blank and a duplicate
     * stays a duplicate. Only a format without a header record falls back to a generated name.
     */
    private fun resolveColumnNames(header: Array<CsvToken>?, columnCount: Int): List<String> =
      (0 until columnCount).map { index ->
        header?.getOrNull(index)?.let { it.value ?: it.text } ?: GridUtilCore.defaultColumnName(index)
      }

    private fun determineColumnType(records: List<Array<CsvToken>>, columnIndex: Int): TypeMerger {
      val values = records.asSequence()
        .take(TYPE_SAMPLE_RECORDS)
        .map { record -> record.getOrNull(columnIndex)?.value }
        .asIterable()
      return CsvImportUtil.getPreferredTypeMergerBasedOnContent(
        values, STRING_MERGER, INTEGER_MERGER, BIG_INTEGER_MERGER, DOUBLE_MERGER, BOOLEAN_MERGER)
    }

    private fun buildRows(offset: Int, records: List<Array<CsvToken>>): List<GridRow> =
      records.mapIndexed { index, record ->
        DataConsumer.Row.create(offset + index, Array(record.size) { record[it].value })
      }

    /**
     * Applies a parsed page, and completes [source] exactly once on every path.
     *
     * The parser reports errors per record, not per file: a record that does not parse is skipped and the ones
     * around it still arrive. Such a page is published, because dropping rows the caller can use would be worse
     * than showing them; only a page that parsed nothing at all is reported as a failed request.
     */
    private fun publishPage(page: CsvPage, offset: Int, source: GridRequestSource) {
      if (page.rows.isEmpty() && page.errors.isNotEmpty()) {
        @Suppress("HardCodedStringLiteral")
        failRequest(source, page.errors.joinToString("\n") { it.message })
        return
      }
      logSkippedRecords(page)
      loadingStarted(offset)
      modelUpdater.removeRows(0, model.rowCount)
      applyColumns(page.columns)
      afterLastRowAdded(addRows(page.rows, 0), source)
    }

    private fun logSkippedRecords(page: CsvPage) {
      if (page.errors.isEmpty()) return
      LOG.warn("CSV source '${file.name}': loaded ${page.rows.size} row(s), " +
               "skipped ${page.errors.size} unparsable one(s), first: ${page.errors.first().message}")
    }

    /** Setting columns clears and re-adds them, dropping per-column state, so re-apply only a schema that changed. */
    private fun applyColumns(columns: List<GridColumn>?) {
      if (columns == null || sameColumns(columns, model.columns)) return
      modelUpdater.setColumns(columns)
    }

    private fun sameColumns(loaded: List<GridColumn>, current: List<GridColumn>): Boolean =
      loaded.size == current.size && loaded.indices.all { index ->
        val loadedColumn = loaded[index]
        val currentColumn = current[index]
        loadedColumn.name == currentColumn.name &&
        loadedColumn.type == currentColumn.type &&
        loadedColumn.typeName == currentColumn.typeName
      }

    private fun failRequest(source: GridRequestSource, message: @Nls String) {
      // requestComplete(false) carries no text, so the message has to travel through the error info.
      notifyRequestError(source, SimpleErrorInfo.create(message))
      notifyRequestFinished(source, false)
    }
  }

  /**
   * Accumulates one page while the parser is pulled batch by batch: `StreamCsvFormatParser.parse()` returns one
   * char-bounded batch at a time, `null` once the file is exhausted, and a records-empty result carrying errors when
   * the header record itself does not parse.
   */
  private class PageCollector(private val offset: Int, private val pageSize: Int) {
    private var recordIndex = 0

    val records: MutableList<Array<CsvToken>> = mutableListOf()
    val errors: MutableList<ErrorRecord> = mutableListOf()

    var header: Array<CsvToken>? = null
      private set

    fun collectFrom(parser: StreamCsvFormatParser) {
      while (!isPageFull()) {
        val batch = parser.parse() ?: break
        errors += batch.errors
        if (header == null) header = batch.header
        // A batch without records means the file is exhausted, or its header record did not parse at all.
        // In the latter case the parser has consumed nothing and would keep handing back the same result, so stop here.
        if (batch.records.isEmpty()) break
        if (!collectRecords(batch.records)) break
      }
    }

    /** @return `false` when the page got full inside this batch and parsing has to stop. */
    private fun collectRecords(batch: List<Array<CsvToken>>): Boolean {
      for (record in batch) {
        if (recordIndex++ >= offset) records += record
        if (isPageFull()) return false
      }
      return true
    }

    private fun isPageFull(): Boolean = !isPageSizeUnlimited(pageSize) && records.size >= pageSize
  }

  companion object {
    /** Chars one parser batch may consume; the loader pulls as many batches as one page needs. */
    private const val DEFAULT_BATCH_CHARS: Int = 64 * 1024

    /** How many records feed the type merger, matching what the document-based CSV hookup samples. */
    private const val TYPE_SAMPLE_RECORDS: Int = 200

    private val LOG = logger<CsvStreamGridDataHookUp>()
  }
}

private typealias CsvToken = StreamCsvFormatParser.Token
