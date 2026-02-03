// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.text

/**
 * Not thread safe if the opposite is not stated explicitly.
 */
abstract class TextFragmentCharSequence : TextFragment, CharSequence {
  protected abstract val enclosingTextCharSequence: WholeTextCharSequence

  abstract fun getAbsolute(absoluteIndex: Long): Char

  abstract operator fun get(index: Long): Char

  override val length: Int
    get() = (toChar - fromChar).toInt()

  override fun isEmpty(): Boolean =
    super<TextFragment>.isEmpty()

  override fun subSequence(startIndex: Int, endIndex: Int): TextFragmentCharSequence =
    fragment(fromChar + startIndex, fromChar + endIndex)

  override fun subSequence(startIndex: Long, endIndex: Long): TextFragmentCharSequence =
    fragment(fromChar + startIndex, fromChar + endIndex)

  override fun fragment(from: Long, to: Long): TextFragmentCharSequence =
    RopeSubSequence(enclosingTextCharSequence, from, to)

  override fun asCharSequence(): TextFragmentCharSequence = this
}

class FragmentOutOfBoundsException(message: String) : IndexOutOfBoundsException(message)

private fun TextFragment.coversFragment(fromChar: Long, toChar: Long): Boolean =
  fromChar >= this.fromChar && toChar <= this.toChar && fromChar <= toChar

private fun TextFragment.checkCoversFragment(fromChar: Long, toChar: Long) {
  if (!coversFragment(fromChar, toChar)) {
    throw FragmentOutOfBoundsException("$fromChar until $toChar is not fragment of ${this.fromChar} until ${this.toChar}")
  }
}

internal class RopeSubSequence(
  override val enclosingTextCharSequence: WholeTextCharSequence,
  override val fromChar: Long = enclosingTextCharSequence.fromChar,
  override val toChar: Long = enclosingTextCharSequence.toChar,
) : TextFragmentCharSequence() {
  init {
    enclosingTextCharSequence.checkCoversFragment(fromChar, toChar)
  }

  override fun text(from: Long, to: Long): String =
    enclosingTextCharSequence.text(from, to)

  override fun toString(): String =
    text(fromChar, toChar)

  override fun get(index: Int): Char =
    try {
      enclosingTextCharSequence.getAbsolute(fromChar + index)
    }
    catch (e: Exception) {
      if (index >= length || index < 0) throw IndexOutOfBoundsException("index:$index, from:$fromChar, to:$toChar")
      else throw e
    }

  override fun get(index: Long): Char =
    try {
      enclosingTextCharSequence.getAbsolute(fromChar + index)
    }
    catch (e: Exception) {
      if (index >= length || index < 0) throw IndexOutOfBoundsException("index:$index, from:$fromChar, to:$toChar")
      else throw e
    }

  override fun getAbsolute(absoluteIndex: Long): Char =
    enclosingTextCharSequence.getAbsolute(absoluteIndex)
}

class WholeTextCharSequence internal constructor(val textView: TextView) : TextFragmentCharSequence() {
  override val fromChar: Long get() = 0
  override val toChar: Long = textView.charCount.toLong()
  override val enclosingTextCharSequence get() = this

  override fun getAbsolute(absoluteIndex: Long): Char =
    textView[absoluteIndex.toInt()]

  override fun get(index: Int): Char =
    getAbsolute(index.toLong())

  override fun get(index: Long): Char =
    getAbsolute(index)

  fun copy(): TextFragmentCharSequence =
    WholeTextCharSequence(textView.text().view())

  override fun text(from: Long, to: Long): String =
    textView.string(from.toInt(), to.toInt())

  override fun toString(): String =
    text(fromChar, toChar)
}

internal open class TextFragmentImpl(
  override val fromChar: Long,
  override val toChar: Long,
  val textView: TextView,
) : TextFragment {
  
  override fun text(from: Long, to: Long): String =
    textView.string(from.toInt(), to.toInt())

  override fun asCharSequence(): TextFragmentCharSequence =
    textView.charSequence(fromChar, toChar)

  override fun toString(): String =
    text(fromChar, toChar)

  override fun fragment(from: Long, to: Long): TextFragment =
    TextFragmentImpl(from, to, textView)
}
