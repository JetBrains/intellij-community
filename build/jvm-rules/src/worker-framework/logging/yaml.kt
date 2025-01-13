package org.jetbrains.bazel.jvm.logging

import java.io.PrintWriter
import java.io.StringWriter
import java.time.format.DateTimeFormatter

object YamlLogRenderer : LogRenderer {
  override fun createStringBuilder(): StringBuilder {
    val sb = StringBuilder()
    sb.append("---\n")
    return sb
  }

  override fun render(sb: StringBuilder, event: LogEvent) {
    sb.setLength(4)

    sb.append("t: ")
    DateTimeFormatter.ISO_INSTANT.formatTo(event.timestamp, sb)
    sb.append('\n')

    if (event.level != null) {
      sb.append("l: ").append(event.level).append('\n')
    }

    val extraFields = event.context
    if (!extraFields.isNullOrEmpty()) {
      for (i in extraFields.indices step 2) {
        sb.append(",\"").append(extraFields[i] as String).append("\":")
        val v = extraFields[i + 1]
        if (v is Int) {
          sb.append(v)
        }
        else {
          appendMessage(v.toString(), sb)
        }
      }
    }

    val message = event.message
    if (message != null) {
      sb.append("m: ")
      appendMessage(message, sb)
    }

    if (event.messageTemplate != null) {
      sb.append("mt: ")
      appendMessage(event.messageTemplate, sb)
    }

    if (event.exception != null) {
      sb.append("x: |")

      val sw = StringWriter()
      val pw = PrintWriter(sw)
      pw.flush()
      event.exception.printStackTrace(pw)
      for (string in sw.buffer.lineSequence()) {
        if (string.isNotEmpty()) {
          sb.append("\n  ").append(string)
        }
      }
      sb.append('\n')
    }
  }
}

private fun appendMessage(message: String, sb: StringBuilder) {
  if (message.contains('\n')) {
    sb.append('|')
    appendMultiline(message, sb)
  }
  else {
    sb.append('"').append(message).append('"')
  }
  sb.append('\n')
}

private fun appendMultiline(message: CharSequence, sb: StringBuilder) {
  for (string in message.lineSequence()) {
    sb.append("\n  ").append(string)
  }
}
