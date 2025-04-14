// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

class IndentingStringBuilder(private val indent: String, private val offset: String) {
  private val stringBuilder = StringBuilder()

  class Context(
    private val indent: String,
    private val currentOffset: String,
    private val stringBuilder: StringBuilder,
  ) {
    fun appendLine(line: String) {
      append(line)
      stringBuilder.append("\n")
    }

    fun append(string: String) {
      stringBuilder.append(currentOffset)
      stringBuilder.append(string)
    }

    fun appendNoOffset(string: String) {
      stringBuilder.append(string)
    }

    fun indent(body: Context.() -> Unit) {
      Context(
        indent = indent,
        currentOffset = currentOffset + indent,
        stringBuilder = stringBuilder
      ).apply(body)
    }

    fun block(prefix: String, suffix: String, body: Context.() -> Unit) {
      appendLine(prefix)
      indent { body() }
      appendLine(suffix)
    }

    internal val length
      get() = stringBuilder.length
  }

  internal fun build(body: Context.() -> Unit) =
    stringBuilder.also {
      Context(indent, offset, it).apply(body)
    }

  override fun toString() = stringBuilder.toString()
}

fun buildStringWithIndentation(indent: String, offset: String = "", body: IndentingStringBuilder.Context.() -> Unit): String =
  IndentingStringBuilder(indent = indent, offset = offset).build(body).toString()
