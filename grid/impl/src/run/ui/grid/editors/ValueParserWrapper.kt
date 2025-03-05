package com.intellij.database.run.ui.grid.editors

import com.intellij.database.run.ReservedCellValue
import com.intellij.database.run.ui.grid.editors.GridCellEditorFactory.ValueParser
import com.intellij.database.run.ui.grid.editors.UnparsedValue.ParsingError
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.equalsIgnoreCase
import java.text.ParseException

class ValueParserWrapper(private val parser: Formatter,
                            private val nullable: Boolean,
                            private val emptyValue: ReservedCellValue?,
                            private val unparsedValueCreator: (String, ParsingError?) -> UnparsedValue) : ValueParser {
  override fun parse(text: String, document: Document?): Any {
    if (emptyValue != null && StringUtil.isEmptyOrSpaces(text)) return emptyValue
    if (nullable && equalsIgnoreCase(text, "null") || equalsIgnoreCase(text, "<null>")) return ReservedCellValue.NULL
    when {
      equalsIgnoreCase(text, "default") || equalsIgnoreCase(text, "<default>") -> return ReservedCellValue.DEFAULT
      equalsIgnoreCase(text, "generated") || equalsIgnoreCase(text, "<generated>") -> return ReservedCellValue.GENERATED
      equalsIgnoreCase(text, "computed") || equalsIgnoreCase(text, "<computed>") -> return ReservedCellValue.COMPUTED
      equalsIgnoreCase(text, "unset") || equalsIgnoreCase(text, "<unset>") -> return ReservedCellValue.COMPUTED
    }
    try {
      return parser.parse(text)
    }
    catch (e: ParseException) {
      return unparsedValueCreator(text, ParsingError(e.localizedMessage, e.errorOffset.takeIf { it != -1 } ?: 0))
    }
  }
}
