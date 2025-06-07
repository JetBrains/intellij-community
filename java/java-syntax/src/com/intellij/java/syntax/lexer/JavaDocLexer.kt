// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.lexer

import com.intellij.java.syntax.element.JavaDocSyntaxTokenType
import com.intellij.java.syntax.element.JavaDocSyntaxTokenTypes
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.util.lexer.LexerBase
import com.intellij.platform.syntax.util.lexer.MergingLexerAdapter
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.text.CharArrayUtilKmp.containLineBreaks

class JavaDocLexer private constructor(isJdk15Enabled: Boolean) : MergingLexerAdapter(
  original = AsteriskStripperLexer(
    myFlex = _JavaDocLexer(isJdk15Enabled),
  ),
  tokenSet = JavaDocSyntaxTokenTypes.spaceCommentsTokenSet
) {
  constructor(level: LanguageLevel) : this(level.isAtLeast(LanguageLevel.JDK_1_5))
}

private class AsteriskStripperLexer(
  private val myFlex: _JavaDocLexer,
) : LexerBase() {
  private lateinit var myBuffer: CharSequence
  private var myBufferIndex = 0
  private var myBufferEndOffset = 0
  private var myTokenEndOffset = 0
  private var myState = 0
  private var myTokenType: SyntaxElementType? = null
  private var myAfterLineBreak = false
  private var myInLeadingSpace = false
  private var myMarkdownMode = false

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    myBuffer = buffer
    myBufferIndex = startOffset
    myBufferEndOffset = endOffset
    myTokenType = null
    myTokenEndOffset = startOffset
    myAfterLineBreak = false
    myInLeadingSpace = false
    myFlex.reset(myBuffer, startOffset, endOffset, initialState)
    selectLexerMode(buffer, startOffset)
  }

  override fun getState(): Int {
    return myState
  }

  override fun getBufferSequence(): CharSequence {
    return myBuffer
  }

  override fun getBufferEnd(): Int {
    return myBufferEndOffset
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

  private fun locateToken() {
    if (myTokenType != null) return
    doLocateToken()

    if (myTokenType === JavaDocSyntaxTokenType.DOC_SPACE) {
      myAfterLineBreak = containLineBreaks(myBuffer, getTokenStart(), getTokenEnd())
    }
  }

  /** Enables markdown mode if necessary  */
  fun selectLexerMode(buffer: CharSequence, startOffset: Int) {
    myMarkdownMode = buffer.startsWith("///", startOffset)
    myFlex.setMarkdownMode(myMarkdownMode)
  }

  fun doLocateToken() {
    if (myTokenEndOffset == myBufferEndOffset) {
      myTokenType = null
      myBufferIndex = myBufferEndOffset
      return
    }

    myBufferIndex = myTokenEndOffset

    if (myAfterLineBreak) {
      myAfterLineBreak = false

      if (myMarkdownMode) {
        while (detectLeadingSlashes()) {
          myTokenEndOffset += 3
        }
      }
      else {
        while (detectLeadingAsterisks()) {
          myTokenEndOffset++
        }
      }

      myInLeadingSpace = true
      if (myBufferIndex < myTokenEndOffset) {
        myTokenType = JavaDocSyntaxTokenType.DOC_COMMENT_LEADING_ASTERISKS
        return
      }
    }

    if (myInLeadingSpace) {
      myInLeadingSpace = false
      var lf = false
      while (myTokenEndOffset < myBufferEndOffset && myBuffer[myTokenEndOffset].isWhitespace()) {
        if (myBuffer[myTokenEndOffset] == '\n') lf = true
        myTokenEndOffset++
      }

      val state = myFlex.yystate()
      if (state == _JavaDocLexer.COMMENT_DATA ||
          state != _JavaDocLexer.SNIPPET_TAG_BODY_DATA && myTokenEndOffset < myBufferEndOffset && myBuffer[myTokenEndOffset].let { it == '@' || it == '{' || it == '\"' || it == '<' }
      ) {
        myFlex.yybegin(_JavaDocLexer.COMMENT_DATA_START)
      }

      if (myBufferIndex < myTokenEndOffset) {
        myTokenType = if (lf || state == _JavaDocLexer.PARAM_TAG_SPACE || state == _JavaDocLexer.TAG_DOC_SPACE || state == _JavaDocLexer.INLINE_TAG_NAME || state == _JavaDocLexer.DOC_TAG_VALUE_IN_PAREN || state == _JavaDocLexer.SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON)
          JavaDocSyntaxTokenType.DOC_SPACE
        else
          JavaDocSyntaxTokenType.DOC_COMMENT_DATA

        return
      }
    }

    flexLocateToken()
  }

  /** @return true for * or *\/
   */
  fun detectLeadingAsterisks(): Boolean {
    return myTokenEndOffset < myBufferEndOffset && myBuffer[myTokenEndOffset] == '*' &&
           (myTokenEndOffset + 1 >= myBufferEndOffset || myBuffer[myTokenEndOffset + 1] != '/')
  }

  /** @return The current token is the start of three leading slashes
   */
  fun detectLeadingSlashes(): Boolean {
    return myTokenEndOffset + 2 < myBufferEndOffset && myBuffer.startsWith("///", myTokenEndOffset)
  }

  fun flexLocateToken() {
    myState = myFlex.yystate()
    myFlex.goTo(myBufferIndex)
    myTokenType = myFlex.advance()
    myTokenEndOffset = myFlex.getTokenEnd()
  }
}