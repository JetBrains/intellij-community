package org.jetbrains.bazel.jvm.logging

import java.io.PrintWriter
import java.io.Writer
import java.time.format.DateTimeFormatter

object JsonLogRenderer : LogRenderer {
  override fun createStringBuilder(): StringBuilder {
    val sb = StringBuilder()
    sb.append("{\"@t\":\"")
    return sb
  }

  override fun render(sb: StringBuilder, event: LogEvent) {
    renderToJson(sb, event)
  }
}

private fun renderToJson(sb: StringBuilder, event: LogEvent) {
  sb.setLength(7)
  DateTimeFormatter.ISO_INSTANT.formatTo(event.timestamp, sb)
  sb.append('"')

  if (event.level != null) {
    sb.append(",\"@l\":\"")
    sb.append(event.level)
    sb.append('"')
  }

  if (event.message != null) {
    sb.append(",\"@m\":\"")
    escapeToJsonStringValue(event.message, sb)
    sb.append('"')
  }

  if (event.messageTemplate != null) {
    sb.append(",\"@m\":\"")
    escapeToJsonStringValue(event.messageTemplate, sb)
    sb.append('"')
  }

  writeCustomFields(event, sb)

  if (event.exception != null) {
    sb.append(",\"@x\":\"")
    event.exception.printStackTrace(object : PrintWriter(JsonStringStringBuilderWriter(sb)) {
      override fun println() {
        sb.append("\\n")
      }

      override fun print(value: String) {
        escapeToJsonStringValue(value, sb)
      }

      override fun print(c: Char) {
        escapeChar(c, sb)
      }
    })
    sb.append('"')
  }

  sb.append("}\n")
}

private fun writeCustomFields(event: LogEvent, sb: StringBuilder) {
  val extraFields = event.context ?: return
  for (i in extraFields.indices step 2) {
    sb.append(",\"").append(extraFields[i] as String).append("\":")
    val v = extraFields[i + 1]
    when (v) {
      is Int -> {
        sb.append(v)
      }

      is List<*> -> serializeList(sb, v.asSequence())
      is Array<*> -> serializeList(sb, v.asSequence())
      is Sequence<*> -> serializeList(sb, v)

      else -> {
        sb.append('"')
        escapeToJsonStringValue(v.toString(), sb)
        sb.append('"')
      }
    }
  }
}

private fun serializeList(sb: StringBuilder, v: Sequence<*>) {
  sb.append('[')
  for (any in v) {
    sb.append('"')
    escapeToJsonStringValue(any.toString(), sb)
    sb.append('"')
    sb.append(',')
  }
  sb.setLength(sb.length - 1)
  sb.append(']')
}

private fun escapeToJsonStringValue(input: CharSequence, sb: StringBuilder) {
  for (char in input) {
    escapeChar(char, sb)
  }
}

private fun escapeChar(char: Char, sb: StringBuilder) {
  when (char) {
    '"' -> sb.append("\\\"")      // Escape double quotes
    '\\' -> sb.append("\\\\")    // Escape backslashes
    '\b' -> sb.append("\\b")     // Escape backspace
    '\u000C' -> sb.append("\\f") // Escape form feed
    '\n' -> sb.append("\\n")     // Escape newline
    '\r' -> sb.append("\\r")     // Escape carriage return
    '\t' -> sb.append("\\t")     // Escape tab
    in '\u0000'..'\u001F' -> {   // Escape other control characters as Unicode
      sb.append(String.format("\\u%04x", char.code))
    }

    else -> sb.append(char)
  }
}

private class JsonStringStringBuilderWriter(private val sb: java.lang.StringBuilder) : Writer() {
  override fun write(value: Int) {
    escapeChar(value.toChar(), sb)
  }

  override fun write(cbuf: CharArray) {
    for (char in cbuf) {
      escapeChar(char, sb)
    }
  }

  override fun append(value: Char): Writer {
    escapeChar(value, sb)
    return this
  }

  override fun append(value: CharSequence): Writer {
    escapeToJsonStringValue(value, sb)
    return this
  }

  override fun append(value: CharSequence, start: Int, end: Int): Writer {
    for (i in start until end) {
      escapeChar(value[i], sb)
    }
    return this
  }

  override fun write(value: String) {
    sb.append(value)
  }

  override fun write(value: String, offset: Int, length: Int) {
    for (i in offset until offset + length) {
      escapeChar(value[i], sb)
    }
  }

  override fun write(value: CharArray, offset: Int, length: Int) {
    for (i in offset until offset + length) {
      escapeChar(value[i], sb)
    }
  }

  override fun flush() {
  }

  override fun close() {
  }

  override fun toString(): String = sb.toString()
}