package com.intellij.database.extractors

import com.intellij.database.datagrid.DataConsumer
import com.intellij.database.run.ReservedCellValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.escapeCharCharacters
import com.intellij.openapi.util.text.StringUtil.escapeStringCharacters
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.sql.Types


fun toJson(value: Any?, formatter: ObjectFormatter, mode: ObjectFormatterMode, newLines: Boolean = false, doubleQuotes: Boolean = true, treatListAsTuple: Boolean = false): String {
  val sb = StringBuilder()
  toJson(value, sb, formatter, mode, newLines, doubleQuotes, treatListAsTuple, 0)
  return sb.toString()
}

private fun toJson(value: Any?,
                   sb: StringBuilder,
                   formatter: ObjectFormatter,
                   mode: ObjectFormatterMode,
                   newLines: Boolean,
                   doubleQuotes: Boolean,
                   treatListAsTuple: Boolean,
                   level: Int) {
  @Suppress("UNCHECKED_CAST")
  when (value) {
    is Array<*>     -> toJson(value.iterator(), value, sb, formatter, mode, newLines, doubleQuotes, treatListAsTuple, level)
    is BooleanArray -> toJson(value.iterator(), value, sb, formatter, mode, newLines, doubleQuotes, treatListAsTuple, level)
    is ByteArray    -> toJson(value.iterator(), value, sb, formatter, mode, newLines, doubleQuotes, treatListAsTuple, level)
    is CharArray    -> toJson(value.iterator(), value, sb, formatter, mode, newLines, doubleQuotes, treatListAsTuple, level)
    is DoubleArray  -> toJson(value.iterator(), value, sb, formatter, mode, newLines, doubleQuotes, treatListAsTuple, level)
    is FloatArray   -> toJson(value.iterator(), value, sb, formatter, mode, newLines, doubleQuotes, treatListAsTuple, level)
    is IntArray     -> toJson(value.iterator(), value, sb, formatter, mode, newLines, doubleQuotes, treatListAsTuple, level)
    is LongArray    -> toJson(value.iterator(), value, sb, formatter, mode, newLines, doubleQuotes, treatListAsTuple, level)
    is ShortArray   -> toJson(value.iterator(), value, sb, formatter, mode, newLines, doubleQuotes, treatListAsTuple, level)
    is Iterable<*> -> toJson(value.iterator(), value, sb, formatter, mode, newLines, doubleQuotes, treatListAsTuple, level)
    is Map<*, *>    -> toJson(value as Map<Any?, Any?>, sb, formatter, mode, newLines, doubleQuotes, treatListAsTuple, level)
    null            -> sb.append("null")
    else -> {
      val jdbcType = guessType(value)
      val column = DataConsumer.Column(0, "", jdbcType, "", value.javaClass.name)
      val v = formatter.objectToString(value, column, DatabaseObjectFormatterConfig.get(mode))
                ?.let { literal ->
                  if (formatter.isStringLiteral(column, value, mode))
                    if (doubleQuotes) "\"" + escapeStringCharacters(literal) + "\""
                    else "\'" + escapeCharCharacters(literal) + "\'"
                  else literal
                }
              ?: "null"
      sb.append(v)
    }
  }
}

fun guessType(value: Any?): Int {
  return when (value) {
    is Map<*, *> -> Types.JAVA_OBJECT
    is List<*> -> Types.ARRAY
    is String -> Types.VARCHAR
    is Int -> Types.INTEGER
    is Long -> Types.BIGINT
    is Double -> Types.DOUBLE
    is Boolean -> Types.BOOLEAN
    is Timestamp -> Types.TIMESTAMP
    is Date -> Types.DATE
    is Time -> Types.TIME
    is java.util.Date -> Types.DATE
    else -> Types.OTHER
  }
}

private fun toJson(value: Iterator<Any?>,
                   originalValue: Any?,
                   sb: StringBuilder,
                   formatter: ObjectFormatter,
                   mode: ObjectFormatterMode,
                   newLines: Boolean,
                   doubleQuotes: Boolean,
                   treatListAsTuple: Boolean,
                   level: Int) {
  if (originalValue is List<*> && treatListAsTuple) sb.append("(")
  else sb.append("[")
  var first = true
  for (v in value) {
    if (v == ReservedCellValue.UNSET) continue
    first = delimiter(sb, first, newLines, level)
    toJson(v, sb, formatter, mode, newLines, doubleQuotes, treatListAsTuple, level + 1)
  }
  if (newLines) sb.append("\n").append(StringUtil.repeat(" ", level * 2))
  if (originalValue is List<*> && treatListAsTuple) sb.append(")")
  else sb.append("]")
}

private fun delimiter(sb: StringBuilder, first: Boolean, newLines: Boolean, level: Int): Boolean {
  if (!first) sb.append(",").append(if (newLines) "" else " ")
  if (newLines) sb.append("\n").append(StringUtil.repeat(" ", (level + 1) * 2))
  return false
}

private fun toJson(value: Map<Any?, Any?>,
                   sb: StringBuilder,
                   formatter: ObjectFormatter,
                   mode: ObjectFormatterMode,
                   newLines: Boolean,
                   doubleQuotes: Boolean,
                   treatListAsTuple: Boolean,
                   level: Int) {
  sb.append("{")
  var first = true
  for ((key, v) in value) {
    if (v == ReservedCellValue.UNSET) continue
    first = delimiter(sb, first, newLines, level)

    toJson(key, sb, formatter, mode, newLines, doubleQuotes, treatListAsTuple, level + 1)
    sb.append(": ")
    toJson(v, sb, formatter, mode, newLines, doubleQuotes, treatListAsTuple, level + 1)
  }
  if (newLines) sb.append("\n").append(StringUtil.repeat(" ", level * 2))
  sb.append("}")
}

fun isJsonString(text: String): Boolean {
  val first = text.firstOrNull { !Character.isWhitespace(it) } ?: return false
  val last = text.lastOrNull { !Character.isWhitespace(it) } ?: return false
  return first == '{' && last == '}' && text.contains(":") || first == '[' && last == ']'
}
