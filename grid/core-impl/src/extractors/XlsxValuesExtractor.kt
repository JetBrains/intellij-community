package com.intellij.database.extractors

import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.extractors.TranspositionAwareExtractor.TranspositionAwareExtraction
import com.intellij.database.run.ReservedCellValue
import com.intellij.database.util.Out
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.TestOnly
import java.io.*
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * @author Liudmila Kornilova
 */
class XlsxValuesExtractor(private val myFormatter: ObjectFormatter) : DataExtractor {
  private var maxRowsPerSheet = 1_048_576
  private var stringsBuffer = 1024 * 1024 * 5
  override fun getFileExtension() = "xlsx"

  override fun supportsText(): Boolean {
    return false;
  }

  override fun startExtraction(
    out: Out,
    allColumns: List<GridColumn>,
    query: String,
    config: ExtractionConfig,
    vararg selectedColumns: Int
  ): TranspositionAwareExtraction {
    return XlsxExtraction(myFormatter, out, allColumns, query, config, selectedColumns, this,
                          XlsxResource(out.toOutputStream(), maxRowsPerSheet, stringsBuffer, query, config),
                          false)
  }

  fun spawnChildExtraction(out: Out,
                           allColumns: MutableList<out GridColumn>,
                           query: String,
                           config: ExtractionConfig,
                           resource: XlsxResource,
                           isOriginallyTransposed: Boolean,
                           vararg selectedColumns: Int): XlsxExtraction {
    return XlsxExtraction(myFormatter, out, allColumns, query, config, selectedColumns, this, resource, isOriginallyTransposed)
  }

  @TestOnly
  fun setMaxRowsPerSheet(v: Int) {
    maxRowsPerSheet = v
  }

  @TestOnly
  fun setStringsBuffer(v: Int) {
    stringsBuffer = v
  }

  class XlsxExtraction(
    val formatter: ObjectFormatter,
    out: Out,
    allColumns: List<GridColumn>,
    query: String,
    config: ExtractionConfig,
    selectedColumnIndices: IntArray,
    val xlsxExtractor: XlsxValuesExtractor,
    val resource: XlsxResource,
    val isOriginallyTransposed: Boolean
  ) : TranspositionAwareExtraction(
    out,
    config,
    allColumns,
    query,
    selectedColumnIndices,
    xlsxExtractor
  ) {

    val zip = resource.zip
    val writer = resource.writer
    val workbook = resource.workbook
    var headerAppended = false

    override fun spawnChildExtraction(out: Out,
                                      allColumns: MutableList<out GridColumn>,
                                      query: String,
                                      config: ExtractionConfig,
                                      isOriginallyTransposed: Boolean,
                                      vararg selectedColumns: Int): DataExtractor.Extraction {
      return xlsxExtractor.spawnChildExtraction(out, allColumns, query, config, resource, isOriginallyTransposed, *selectedColumns)
    }

    override fun updateColumns(columns: Array<GridColumn>) {
      myAllColumns = listOf(*columns)
      setHeader()
    }

    override fun doAppendHeader(appendNewLine: Boolean) {
      if (!headerAppended && !isOriginallyTransposed) {
        setHeader()
      }
    }

    override fun doAppendData(rows: List<GridRow>) {
      doAppendHeader(false)

      val selectedColumnIndicesWithColumnHeader: IntArray = if (!isOriginallyTransposed) mySelectedColumnIndices
      else {
        val res = IntArray(mySelectedColumnIndices.size + 1)
        mySelectedColumnIndices.copyInto(res, destinationOffset = 1)
        res[0] = 0
        res
      }
      val selectedColumns = GridExtractorsUtilCore.getNonEmptySelection(myAllColumns, selectedColumnIndicesWithColumnHeader)
      val columnsMap = GridExtractorsUtilCore.getColumnNumsToColumnsMapping(myAllColumns)
      for (row in rows) {
        workbook.appendRow(selectedColumns.map { selectedColumn ->
          val column = columnsMap.get(selectedColumn)
          if (column == null) null
          else {
            val value = column.getValue(row)
            when {
              value == null || value == ReservedCellValue.UNSET -> null
              value is Number && fitsIntoXlsxNumber(value) -> value
              value is Boolean -> value
              else -> formatter.objectToString(value, column, DatabaseObjectFormatterConfig(ObjectFormatterMode.DEFAULT))
            }
          }
        })
      }
      writer.flush()
    }

    private fun setHeader() {
      workbook.let { workbook ->
        val selectedColumns = GridExtractorsUtilCore.getNonEmptySelection(myAllColumns, mySelectedColumnIndices)
        val columnsMap = GridExtractorsUtilCore.getColumnNumsToColumnsMapping(myAllColumns)
        workbook.header = selectedColumns.map { selectedColumn -> columnsMap.get(selectedColumn)?.getName() }
        headerAppended = true
      }
    }

    override fun complete() {
      workbook.end()
      writer.flush()
      zip.finish()
    }

    override fun completeBatch() {
    }
  }

  class XlsxResource(
    out: OutputStream,
    maxRowsPerSheet: Int,
    stringsBuffer: Int,
    query: String,
    config: ExtractionConfig
  ) {
    val zip = ZipOutputStream(out, StandardCharsets.UTF_8)
    val writer = BufferedWriter(OutputStreamWriter(zip, StandardCharsets.UTF_8))
    val workbook = XlsxWorkbook(zip, writer, maxRowsPerSheet, stringsBuffer, query, config)
  }

  private class SharedStrings(private val stringsBuffer: Int) {
    var totalStringsCount = 0
    var uniqueCount = 0
    var totalLength = 0
    val sharedStrings = LinkedHashMap<String, Int>()
    val tmpFile = FileUtil.createTempFile("xlsxExtractor", ".gz", true)
    val tmpWriter = BufferedWriter(OutputStreamWriter(GZIPOutputStream(FileOutputStream(tmpFile)), StandardCharsets.UTF_8))

    fun getId(v: String): Int {
      totalStringsCount++
      val res = sharedStrings.getOrPut(v) {
        totalLength += v.length
        uniqueCount++
      }
      if (totalLength >= stringsBuffer) {
        flushToTempFile()
      }
      return res
    }

    private fun flushToTempFile() {
      sharedStrings.keys.forEach { str ->
        tmpWriter.append("<si><t>")
          .append(StringUtil.escapeXmlEntities(str).replace(INVALID_XML_CHARS, "?"))
          .append("</t></si>")
      }
      tmpWriter.append("\n")
      tmpWriter.flush()
      totalLength = 0
      sharedStrings.clear()
    }

    fun write(writer: BufferedWriter) {
      if (sharedStrings.isNotEmpty()) {
        flushToTempFile()
      }
      tmpWriter.close()
      writer.append(SHARED_STRINGS_HEADER)
      writer.append("<sst count=\"").append(totalStringsCount.toString())
        .append("\" uniqueCount=\"").append(uniqueCount.toString())
        .append("\" xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
      BufferedReader(InputStreamReader(GZIPInputStream(FileInputStream(tmpFile)), StandardCharsets.UTF_8)).use { reader ->
        reader.lineSequence().forEach { line ->
          writer.append(line).append("\n")
        }
      }
      writer.append("</sst>")
    }
  }

  class XlsxWorkbook(
    val zip: ZipOutputStream,
    val writer: BufferedWriter,
    val maxRowsPerSheet: Int,
    stringsBuffer: Int,
    val query: String,
    val config: ExtractionConfig
  ) {
    private val sharedStrings = SharedStrings(stringsBuffer)
    private var sheetNames = mutableListOf<String>()
    private var sheet = newSheet()
    var header: List<String?>? = null

    fun appendRow(row: List<Any?>) {
      try {
        if (sheet.rowCount >= maxRowsPerSheet) {
          endSheet()
          sheet = newSheet()
        }

        if (sheet.rowCount == 0) {
          header?.let { h -> sheet.appendRow(writer, h) }
        }
        sheet.appendRow(writer, row)
      }
      catch (e: Exception) {
        LOG.error(e)
        throw e
      }
    }

    private fun endSheet() {
      sheet.end(writer)
      writer.flush()
      zip.closeEntry()
    }

    private fun newSheet(name: String? = null): XlsxSheetBuilder {
      sheetNames.add(name ?: "Result ${sheetNames.size + 1}")
      zip.putNextEntry(ZipEntry("$WORKSHEETS_DIR/sheet${sheetNames.size}.xml"))
      return XlsxSheetBuilder(sharedStrings)
    }

    fun end() {
      try {
        endSheet()
        if (config.addQuery == true && isValidCellContent(query)) {
          sheet = newSheet("Query")
          sheet.appendRow(writer, listOf(query))
          endSheet()
        }

        fun entry(name: String, block: () -> Unit) {
          zip.putNextEntry(ZipEntry(name))
          block()
          writer.flush()
          zip.closeEntry()
        }

        entry(RELS_NAME) {
          writer.append(RELS_CONTENT)
        }
        entry(WORKBOOK_RELS_NAME) {
          writer.append(WORKBOOK_RELS_HEADER)
          for (i in 0 until sheetNames.size) {
            writer.append(
              "<Relationship Id=\"rId${i + 2}\" Target=\"worksheets/sheet${i + 1}.xml\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\"/>")
          }
          writer.append(WORKBOOK_RELS_FOOTER)
        }
        entry(SHARED_STRINGS_NAME) {
          sharedStrings.write(writer)
        }
        entry(WORKBOOK_NAME) {
          writer.append(WORKBOOK_HEADER)
          sheetNames.forEachIndexed { i, name ->
            writer.append("<sheet name=\"$name\" r:id=\"rId${i + 2}\" sheetId=\"${i + 1}\"/>")
          }
          writer.append(WORKBOOK_FOOTER)
        }
        entry(CONTENT_TYPES_NAME) {
          writer.append(CONTENT_TYPES_HEADER)
          for (i in 0 until sheetNames.size) {
            writer.append(
              "<Override ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\" PartName=\"/xl/worksheets/sheet${i + 1}.xml\"/>")
          }
          writer.append(CONTENT_TYPES_FOOTER)
        }
      }
      catch (e: Exception) {
        LOG.error(e)
        throw e
      }
      finally {
        sharedStrings.tmpFile.delete()
      }
    }

    private fun isValidCellContent(text: String): Boolean {
      return text.length < 32767
    }
  }


  private class XlsxSheetBuilder(private val sharedStrings: SharedStrings) {
    private var headerAppended = false
    var rowCount = 0

    fun appendRow(writer: BufferedWriter, row: List<Any?>) {
      rowCount++
      if (!headerAppended) {
        writer.append(SHEET_HEADER)
        headerAppended = true
      }

      writer.append("<row>")
      for (v in row) {
        if (v == null) {
          writer.append("<c/>")
          continue
        }
        val type: String
        val value: String
        when (v) {
          is Number -> {
            type = "n"
            value = v.toString()
          }
          is Boolean -> {
            type = "b"
            value = if (v) "1" else "0"
          }
          else -> {
            type = "s"
            value = sharedStrings.getId(v.toString()).toString()
          }
        }
        writer.append("<c t=\"")
          .append(type)
          .append("\"><v>")
          .append(value)
          .append("</v></c>")
      }
      writer.append("</row>")
    }

    fun end(writer: BufferedWriter) {
      writer.append(SHEET_FOOTER)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(XlsxValuesExtractor::class.java)
    const val RELS_NAME = "_rels/.rels"
    const val WORKBOOK_NAME = "xl/workbook.xml"
    const val WORKSHEETS_DIR = "xl/worksheets"
    const val WORKBOOK_RELS_NAME = "xl/_rels/workbook.xml.rels"
    const val SHARED_STRINGS_NAME = "xl/sharedStrings.xml"
    const val CONTENT_TYPES_NAME = "[Content_Types].xml"
    const val RELS_CONTENT = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n  <Relationship Id=\"rId1\" Target=\"xl/workbook.xml\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\"/>\n</Relationships>"
    const val WORKBOOK_RELS_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n<Relationship Id=\"rId1\" Target=\"sharedStrings.xml\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\"/>"
    const val WORKBOOK_RELS_FOOTER = "</Relationships>"
    const val WORKBOOK_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n<workbookPr date1904=\"false\"/><bookViews><workbookView activeTab=\"0\"/></bookViews>\n<sheets>"
    const val WORKBOOK_FOOTER = "</sheets>\n</workbook>"
    const val CONTENT_TYPES_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n<Default ContentType=\"application/vnd.openxmlformats-package.relationships+xml\" Extension=\"rels\"/>\n<Default ContentType=\"application/xml\" Extension=\"xml\"/>\n<Override ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\" PartName=\"/xl/sharedStrings.xml\"/>\n<Override ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\" PartName=\"/xl/workbook.xml\"/>"
    const val CONTENT_TYPES_FOOTER = "</Types>"
    const val SHEET_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
    const val SHEET_FOOTER = "</sheetData></worksheet>"
    const val SHARED_STRINGS_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
    val INVALID_XML_CHARS = Regex("[^\u0009\r\n\u0020-\uD7FF\uE000-\uFFFD\ud800\udc00-\udbff\udfff]")
  }
}

private fun fitsIntoXlsxNumber(value: Number): Boolean {
  val count = trimAndCountDigits(value)
  return count != null && count <= 15
}

private fun trimAndCountDigits(value: Number): Int? {
  try {
    val bigDecimal = BigDecimal(value.toString()).stripTrailingZeros()
    try {
      val bigInteger = bigDecimal.toBigIntegerExact()
      return countDigits(trimRightZeros(bigInteger))
    }
    catch (ignored: ArithmeticException) {
    }
    return bigDecimal.precision()
  }
  catch (e: NumberFormatException) {
    return null
  }
}

private fun trimRightZeros(bigInteger: BigInteger): BigInteger {
  var num = bigInteger
  while (num.remainder(BigInteger.TEN) == BigInteger.ZERO && num != BigInteger.ZERO) {
    num = num.divide(BigInteger.TEN)
  }
  return num
}

private fun countDigits(bigInteger: BigInteger): Int {
  var num = bigInteger
  var count = 0
  while (num != BigInteger.ZERO) {
    num = num.divide(BigInteger.TEN)
    count++
  }
  return count
}