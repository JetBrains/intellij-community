// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text.impl

import andel.text.*
import andel.text.TextFragmentImpl

internal class TextLineImpl(
    override val lineNumber: Long,
    fromChar: Long,
    toChar: Long,
    override val includesSeparator: Boolean,
    private val tryIncludeSeparator: Boolean,
    textView: TextView,
) : TextFragmentImpl(fromChar, toChar, textView), TextLine {

  override val toCharExcludingSeparator: Long
    get() = if (includesSeparator) toChar - 1 else toChar

  override val toCharIncludingSeparator: Long
    get() = if (includesSeparator || isLastLine()) toChar else toChar + 1

  override fun withoutSeparator(): TextLine =
    TextLineImpl(lineNumber = lineNumber,
                 fromChar = fromChar,
                 toChar = toCharExcludingSeparator,
                 includesSeparator = false,
                 tryIncludeSeparator = false,
                 textView = textView)

  override fun next(): TextLine? =
    if (isLastLine()) null
    else textView.textLine(lineNumber.line + 1.line, tryIncludeSeparator)

  override fun prev(): TextLine? =
    if (isFirstLine()) null
    else textView.textLine(lineNumber.line - 1.line, tryIncludeSeparator)

  override fun isFirstLine(): Boolean =
    lineNumber == 0L

  override fun isLastLine(): Boolean =
    lineNumber.line == textView.lastLine
}