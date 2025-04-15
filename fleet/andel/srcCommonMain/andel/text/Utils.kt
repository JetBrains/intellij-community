// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

private val WHITESPACES = arrayOf(' ', '\t')

fun isWhiteSpaceLine(text: TextView, lineNumber: Int): Boolean {
  val lineStart = text.lineStartOffset(lineNumber.line)
  val lineEnd = text.lineEndOffset(lineNumber.line)
  return (lineStart until lineEnd).all { text[it] in WHITESPACES }
}

fun lineHasTabIndentation(textView: TextView, lineNumber: Int): Boolean =
  indentStringOfLine(textView, lineNumber.line).contains('\t')

fun tabCount(textView: TextView, lineNumber: Int): Int =
  indentStringOfLine(textView, lineNumber.line).count { it == '\t' }

fun TextFragment.indentOfLine(): Long {
  return this.asCharSequence().takeWhile { it in WHITESPACES }.count().toLong()
}

fun TextFragment.indentFragmentOfLine(): TextFragment {
  val size = asCharSequence().takeWhile { it in WHITESPACES }.count()
  return this.subSequence(0, size.toLong())
}

fun TextFragment.leadingWhitespace(): String {
  return this.asCharSequence().takeWhile { it in WHITESPACES }.toString()
}

fun TextFragment.dropLeadingWhitespace(): TextFragment {
  return fragment(from = this.fromChar + this.indentOfLine())
}

fun TextFragment.trailingWhitespace(): String {
  return this.asCharSequence().takeLastWhile { it in WHITESPACES }.toString()
}

fun indentRange(textView: TextView, lineNumber: LineNumber): TextRange {
  val lineStartOffset = textView.lineStartOffset(lineNumber)
  val lineEndOffset = textView.lineEndOffset(lineNumber)
  for (i in (lineStartOffset until lineEndOffset)) {
    if (textView[i] !in WHITESPACES) return TextRange(lineStartOffset, i)
  }
  return TextRange(lineStartOffset, lineEndOffset)
}

fun indentStringOfLine(textView: TextView, lineNumber: LineNumber): CharSequence =
  textView.string(indentRange(textView, lineNumber))

fun indentOfLine(textView: TextView, lineNumber: LineNumber): Int = indentRange(textView, lineNumber).length.toInt()

fun leadingWhiteSpaceAtOffset(textView: TextView, offset: Int): Int =
  when {
    offset == 0 -> 0
    else -> {
      var cnt = 0
      for (i in offset - 1 downTo 0) {
        if (textView[i] in WHITESPACES) cnt++
        else break
      }
      cnt
    }
  }

fun trailingWhiteSpaceAtOffset(textView: TextView, offset: Int): Int {
  var cnt = 0
  for (i in offset until textView.charCount) {
    if (textView[i] in WHITESPACES) cnt++
    else break
  }
  return cnt
}


fun shiftBackward(text: Text, offset: Long, vararg charsToSkip: Char): Long =
  shiftBackward(text, offset) { it in charsToSkip }

fun shiftForward(text: Text, offset: Long, vararg charsToSkip: Char): Long {
  return shiftForward(text, offset) { it in charsToSkip}  
}

fun shiftForward(text: Text, offset: Long, charsToSkip: (Char) -> Boolean): Long {
  val textView = text.view()
  for (i in offset.toInt() until text.charCount) {
    if (!charsToSkip(textView[i])) return i.toLong()
  }
  return text.charCount.toLong()
}

fun shiftBackward(text: Text, offset: Long, charsToSkip: (Char) -> Boolean): Long {
  val textView = text.view()
  for (i in offset.toInt() downTo 0) {
    if (!charsToSkip(textView[i])) return i.toLong()
  }
  return 0
}

/**
 * Copypasted version of com.intellij.util.text.CharArrayUtil.regionMatches(java.lang.CharSequence, int, java.lang.CharSequence)
 */
fun regionMatches(text: Text, offset: Long, s: String): Boolean {
  if (offset + s.length > text.charCount.toLong()) return false
  if (offset < 0) return false
  val allSymbolsMatch = text.view().charSequence(offset, offset + s.length)
    .asSequence()
    .zip(s.asSequence())
    .all { (left, right) -> left == right }

  return allSymbolsMatch
}
