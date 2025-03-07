// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.lexer

import com.intellij.java.syntax.element.JavaDocSyntaxElementType
import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.element.SyntaxTokenTypes.WHITE_SPACE
import com.intellij.platform.syntax.impl.fastutil.ints.IntArrayList
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.lexer.LexerPosition
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.text.CharArrayUtilKmp.fromSequenceWithoutCopying

class JavaLexer(level: LanguageLevel) : Lexer {
  private val myFlexLexer: _JavaLexer = _JavaLexer(level)
  private val myStateStack = IntArrayList(1)
  private lateinit var myBuffer: CharSequence
  private var myBufferArray: CharArray? = null
  private var myBufferIndex = 0
  private var myBufferEndOffset = 0
  private var myTokenEndOffset = 0 // positioned after the last symbol of the current token
  private var myTokenType: SyntaxElementType? = null

  /** The length of the last valid unicode escape (6 or greater), or 1 when no unicode escape was found.  */
  private var mySymbolLength = 1

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    myBuffer = buffer
    myBufferArray = fromSequenceWithoutCopying(buffer)
    myBufferIndex = startOffset
    myBufferEndOffset = endOffset
    myTokenType = null
    myTokenEndOffset = startOffset
    mySymbolLength = 1
    myStateStack.push(initialState)
    myFlexLexer.reset(myBuffer, startOffset, endOffset, 0)
  }

  override fun start(buf: CharSequence, start: Int, end: Int) {
    start(buf, start, end, STATE_DEFAULT)
  }

  override fun start(buf: CharSequence) {
    start(buf, 0, buf.length)
  }

  override fun getState(): Int {
    return myStateStack.topInt()
  }

  override fun getTokenType(): SyntaxElementType? {
    locateToken()
    return myTokenType
  }

  override fun getTokenStart(): Int {
    locateToken()
    return myBufferIndex
  }

  override fun getTokenEnd(): Int {
    locateToken()
    return myTokenEndOffset
  }

  override fun advance() {
    locateToken()
    myTokenType = null
  }

  override fun getCurrentPosition(): LexerPosition {
    val offset = getTokenStart()
    val intState = getState()
    return LexerPositionImpl(offset, intState)
  }

  override fun restore(position: LexerPosition) {
    start(getBufferSequence(), position.offset, getBufferEnd(), position.state)
  }

  /**
   * Handles whitespace, comment, string literal, text block and string template tokens. Other tokens are handled by calling
   * the flex lexer.
   */
  private fun locateToken() {
    if (myTokenType != null) return

    if (myTokenEndOffset == myBufferEndOffset) {
      myBufferIndex = myBufferEndOffset
      return
    }

    myBufferIndex = myTokenEndOffset

    val c = locateCharAt(myBufferIndex)
    when (c) {
      ' ', '\t', '\n', '\r', '\u000C' -> {
        myTokenType = WHITE_SPACE
        myTokenEndOffset = getWhitespaces(myBufferIndex + mySymbolLength)
      }
      '{' -> {
        val count1 = myStateStack.topInt() shr 16
        if (count1 > 0) myStateStack.push((myStateStack.popInt() and STATE_TEXT_BLOCK_TEMPLATE) or ((count1 + 1) shl 16))
        myTokenType = JavaSyntaxTokenType.LBRACE
        myTokenEndOffset = myBufferIndex + mySymbolLength
      }
      '}' -> {
        val count2 = myStateStack.topInt() shr 16
        var updateToken = true
        if (count2 > 0) {
          if (count2 != 1) {
            myStateStack.push((myStateStack.popInt() and STATE_TEXT_BLOCK_TEMPLATE) or ((count2 - 1) shl 16))
          }
          else {
            val state = myStateStack.popInt()
            if (myStateStack.size == 0) myStateStack.push(STATE_DEFAULT)
            if ((state and STATE_TEXT_BLOCK_TEMPLATE) != 0) {
              val fragment = locateLiteralEnd(myBufferIndex + mySymbolLength, LiteralType.TEXT_BLOCK)
              myTokenType = if (fragment) JavaSyntaxTokenType.TEXT_BLOCK_TEMPLATE_MID else JavaSyntaxTokenType.TEXT_BLOCK_TEMPLATE_END
            }
            else {
              val fragment = locateLiteralEnd(myBufferIndex + mySymbolLength, LiteralType.STRING)
              myTokenType = if (fragment) JavaSyntaxTokenType.STRING_TEMPLATE_MID else JavaSyntaxTokenType.STRING_TEMPLATE_END
            }
            updateToken = false
          }
        }
        if (updateToken) {
          myTokenType = JavaSyntaxTokenType.RBRACE
          myTokenEndOffset = myBufferIndex + mySymbolLength
        }
      }
      '/' -> if (myBufferIndex + mySymbolLength >= myBufferEndOffset) {
        myTokenType = JavaSyntaxTokenType.DIV
        myTokenEndOffset = myBufferEndOffset
      }
      else {
        val l1 = mySymbolLength
        val nextChar = locateCharAt(myBufferIndex + l1)
        if (nextChar == '/') {
          val l2 = mySymbolLength
          if (myBufferIndex + l1 + l2 < myBufferEndOffset && locateCharAt(myBufferIndex + l1 + l2) == '/') {
            // Java 23 Markdown comments
            myTokenType = JavaDocSyntaxElementType.DOC_COMMENT
            myTokenEndOffset = getClosingMarkdownComment(myBufferIndex + l1 + l2)
          }
          else {
            myTokenType = JavaSyntaxTokenType.END_OF_LINE_COMMENT
            myTokenEndOffset = getLineTerminator(myBufferIndex + l1 + l2)
          }
        }
        else if (nextChar == '*') {
          val l2 = mySymbolLength
          if (myBufferIndex + l1 + l2 < myBufferEndOffset && locateCharAt(myBufferIndex + l1 + l2) == '*') {
            val l3 = mySymbolLength
            if (myBufferIndex + l1 + l2 + l3 < myBufferEndOffset && locateCharAt(myBufferIndex + l1 + l2 + l3) == '/') {
              myTokenType = JavaSyntaxTokenType.C_STYLE_COMMENT
              myTokenEndOffset = myBufferIndex + l1 + l2 + l3 + mySymbolLength
            }
            else {
              myTokenType = JavaDocSyntaxElementType.DOC_COMMENT
              myTokenEndOffset = getClosingComment(myBufferIndex + l1 + l2 + l3)
            }
          }
          else {
            myTokenType = JavaSyntaxTokenType.C_STYLE_COMMENT
            myTokenEndOffset = getClosingComment(myBufferIndex + l1 + l2 + mySymbolLength)
          }
        }
        else {
          flexLocateToken()
        }
      }
      '#' -> if (myBufferIndex == 0 && mySymbolLength == 1 && myBufferEndOffset > 1 && charAt(1) == '!') {
        myTokenType = JavaSyntaxTokenType.END_OF_LINE_COMMENT
        myTokenEndOffset = getLineTerminator(2)
      }
      else {
        flexLocateToken()
      }
      '\'' -> {
        myTokenType = JavaSyntaxTokenType.CHARACTER_LITERAL
        locateLiteralEnd(myBufferIndex + mySymbolLength, LiteralType.CHAR)
      }
      '"' -> {
        val l1 = mySymbolLength
        if (myBufferIndex + l1 < myBufferEndOffset && locateCharAt(myBufferIndex + l1) == '"') {
          val l2 = mySymbolLength
          if (myBufferIndex + l1 + l2 < myBufferEndOffset && locateCharAt(myBufferIndex + l1 + l2) == '"') {
            val fragment = locateLiteralEnd(myBufferIndex + l1 + l2 + mySymbolLength, LiteralType.TEXT_BLOCK)
            myTokenType = if (fragment) JavaSyntaxTokenType.TEXT_BLOCK_TEMPLATE_BEGIN else JavaSyntaxTokenType.TEXT_BLOCK_LITERAL
          }
          else {
            myTokenType = JavaSyntaxTokenType.STRING_LITERAL
            myTokenEndOffset = myBufferIndex + l1 + l2
          }
        }
        else {
          val fragment = locateLiteralEnd(myBufferIndex + l1, LiteralType.STRING)
          myTokenType = if (fragment) JavaSyntaxTokenType.STRING_TEMPLATE_BEGIN else JavaSyntaxTokenType.STRING_LITERAL
        }
      }
      else -> flexLocateToken()
    }

    if (myTokenEndOffset > myBufferEndOffset) {
      myTokenEndOffset = myBufferEndOffset
    }
  }

  private fun getWhitespaces(offset: Int): Int {
    return getChars(offset, " \t\n\r\u000c")
  }

  private fun getSimpleWhitespaces(offset: Int): Int {
    return getChars(offset, " \t")
  }

  /**
   * @return The new position if none of the chars were detected
   */
  private fun getChars(offset: Int, charsToDetect: CharSequence): Int {
    var pos = offset
    while (pos < myBufferEndOffset) {
      var detected = false
      val c = locateCharAt(pos)
      for (i in 0..<charsToDetect.length) {
        if (charsToDetect[i] == c) {
          pos += mySymbolLength
          detected = true
          break
        }
      }

      if (!detected) break
    }

    return pos
  }

  private fun flexLocateToken() {
    myFlexLexer.goTo(myBufferIndex)
    myTokenType = myFlexLexer.advance()
    myTokenEndOffset = myFlexLexer.getTokenEnd()
  }

  /**
   * @param offset  the offset to start.
   * @param literalType  the type of string literal.
   * @return `true` if this is a string template fragment, `false` otherwise.
   */
  private fun locateLiteralEnd(offset: Int, literalType: LiteralType): Boolean {
    var pos = offset

    while (pos < myBufferEndOffset) {
      val c = locateCharAt(pos)

      if (c == '\\') {
        pos += mySymbolLength
        // on (encoded) backslash we also need to skip the next symbol (e.g. \\u005c" is translated to \")
        if (pos < myBufferEndOffset) {
          if (locateCharAt(pos) == '{' && literalType != LiteralType.CHAR) {
            pos += mySymbolLength
            myTokenEndOffset = pos
            if (myStateStack.topInt() == 0) myStateStack.popInt()
            if (literalType === LiteralType.TEXT_BLOCK) {
              myStateStack.push(STATE_TEXT_BLOCK_TEMPLATE or (1 shl 16))
            }
            else {
              myStateStack.push(STATE_DEFAULT or (1 shl 16))
            }
            return true
          }
        }
      }
      else if (c == literalType.c) {
        if (literalType === LiteralType.TEXT_BLOCK) {
          if ((mySymbolLength.let { pos += it; pos }) < myBufferEndOffset && locateCharAt(pos) == '"') {
            if ((mySymbolLength.let { pos += it; pos }) < myBufferEndOffset && locateCharAt(pos) == '"') {
              myTokenEndOffset = pos + mySymbolLength
              return false
            }
          }
          continue
        }
        else {
          myTokenEndOffset = pos + mySymbolLength
          return false
        }
      }
      else if ((c == '\n' || c == '\r') && mySymbolLength == 1 && literalType != LiteralType.TEXT_BLOCK) {
        myTokenEndOffset = pos
        return false
      }
      pos += mySymbolLength
    }
    myTokenEndOffset = pos
    return false
  }

  private fun getClosingMarkdownComment(offset: Int): Int {
    var pos = offset
    while (pos < myBufferEndOffset) {
      pos = getLineTerminator(pos)
      // Account for whitespaces beforehand
      var newPos = getSimpleWhitespaces(pos + mySymbolLength)

      newPos = getCharSeqAt(newPos, "///")
      if (newPos == -1) break
      pos = newPos
    }

    return pos
  }

  private fun getClosingComment(offset: Int): Int {
    var pos = offset

    while (pos < myBufferEndOffset) {
      val c = locateCharAt(pos)
      pos += mySymbolLength
      if (c == '*' && pos < myBufferEndOffset && locateCharAt(pos) == '/') break
    }

    return pos + mySymbolLength
  }

  private fun getLineTerminator(offset: Int): Int {
    var pos = offset

    while (pos < myBufferEndOffset) {
      val c = locateCharAt(pos)
      if (c == '\r' || c == '\n') break
      pos += mySymbolLength
    }

    return pos
  }

  /**
   * @return The position after of the last char, -1 otherwise
   */
  @Suppress("SameParameterValue")
  private fun getCharSeqAt(offset: Int, charSequence: CharSequence): Int {
    var pos = offset
    for (i in 0..<charSequence.length) {
      if (!isLocatedCharAt(pos, charSequence[i])) return -1
      pos += mySymbolLength
    }
    return pos
  }

  private fun isLocatedCharAt(offset: Int, charToDetect: Char): Boolean {
    return (offset < myBufferEndOffset) && locateCharAt(offset) == charToDetect
  }

  private fun charAt(offset: Int): Char {
    return myBufferArray?.let { it[offset] } ?: myBuffer[offset]
  }

  private fun locateCharAt(offset: Int): Char {
    mySymbolLength = 1
    val first = charAt(offset)
    if (first != '\\') return first
    var pos = offset + 1
    if (pos < myBufferEndOffset && charAt(pos) == '\\') return first
    var escaped = true
    var i = offset
    while (--i >= 0 && charAt(i) == '\\') escaped = !escaped
    if (!escaped) return first
    if (pos < myBufferEndOffset && charAt(pos) != 'u') return first
    @Suppress("ControlFlowWithEmptyBody")
    while (++pos < myBufferEndOffset && charAt(pos) == 'u');
    if (pos + 3 >= myBufferEndOffset) return first
    var result = 0
    val max = pos + 4
    while (pos < max) {
      result = result shl 4
      val c = charAt(pos)
      if ('0' <= c && c <= '9') result += c.code - '0'.code
      else if ('a' <= c && c <= 'f') result += (c.code - 'a'.code) + 10
      else if ('A' <= c && c <= 'F') result += (c.code - 'A'.code) + 10
      else return first
      pos++
    }
    mySymbolLength = pos - offset
    return result.toChar()
  }

  override fun getBufferSequence(): CharSequence {
    return myBuffer
  }

  override fun getBufferEnd(): Int {
    return myBufferEndOffset
  }

  private fun IntArrayList.topInt(): Int = get(size - 1)
  private fun IntArrayList.push(value: Int) = this.add(value)
  private fun IntArrayList.popInt(): Int = removeAt(size - 1)
}

private enum class LiteralType(val c: Char) {
  STRING('"'), CHAR('\''), TEXT_BLOCK('"')
}

private class LexerPositionImpl(override val offset: Int, override val state: Int) : LexerPosition

private const val STATE_DEFAULT = 0
private const val STATE_TEXT_BLOCK_TEMPLATE = 1
