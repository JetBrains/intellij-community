// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.common

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.LexerBase
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import javax.swing.Icon

/**
 * A test language that tokenizes input on spaces.
 * Each word (sequence of non-space characters) becomes a separate PsiElement in the PSI tree.
 *
 * PSI tree for "hello world foo":
 * ```
 * File [0, 15]
 *   WORD_ELEMENT [0, 5]   ("hello")
 *   PsiWhiteSpace [5, 6]  (" ")
 *   WORD_ELEMENT [6, 11]  ("world")
 *   PsiWhiteSpace [11, 12] (" ")
 *   WORD_ELEMENT [12, 15] ("foo")
 * ```
 */
internal object SpaceTokenizingLanguage : Language("SpaceTokenizing")

private val WORD_TOKEN = IElementType("WORD", SpaceTokenizingLanguage)
private val WORD_ELEMENT = IElementType("WORD_ELEMENT", SpaceTokenizingLanguage)
private val FILE_ELEMENT_TYPE = IFileElementType("SpaceTokenizingFile", SpaceTokenizingLanguage)

/**
 * Use with [com.intellij.testFramework.fixtures.CodeInsightTestFixture.configureByText] passing this file type directly.
 * This avoids the need to register the file type with [com.intellij.openapi.fileTypes.FileTypeManager].
 */
internal object SpaceTokenizingFileType : LanguageFileType(SpaceTokenizingLanguage) {
  override fun getName(): String = "SpaceTokenizing"
  override fun getDescription(): String = "Space-tokenizing test file type"
  override fun getDefaultExtension(): String = "stok"
  override fun getIcon(): Icon? = null
}

internal fun spaceTokenizingLanguageFixture(): TestFixture<Unit> = testFixture("SpaceTokenizingLanguage") {
  val disposable = Disposer.newDisposable("SpaceTokenizingLanguage")
  LanguageParserDefinitions.INSTANCE.addExplicitExtension(
    SpaceTokenizingLanguage,
    SpaceTokenizingParserDefinition(),
    disposable
  )
  initialized(Unit) {
    Disposer.dispose(disposable)
  }
}

internal suspend fun assertCustomPsiTree(psiFile: PsiFile) {
  readAction {
    val children = psiFile.children
    check(children.size > 1) { "PSI tree should have more than 1 child element, got ${children.size}" }
    check(psiFile.textLength == psiFile.textRange.length) { "PSI file range should cover the entire file" }
  }
}

private class SpaceTokenizingFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, SpaceTokenizingLanguage) {
  override fun getFileType(): FileType = SpaceTokenizingFileType
}

/**
 * Lexer that splits input into WORD tokens (non-space characters) and WHITE_SPACE tokens (spaces).
 */
private class SpaceTokenizingLexer : LexerBase() {
  private lateinit var buffer: CharSequence
  private var bufferEnd = 0
  private var tokenStart = 0
  private var tokenEnd = 0

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    this.buffer = buffer
    this.bufferEnd = endOffset
    tokenStart = startOffset
    tokenEnd = locateTokenEnd(startOffset)
  }

  override fun getState(): Int = 0

  override fun getTokenType(): IElementType? {
    if (tokenStart >= bufferEnd) return null
    return if (buffer[tokenStart] == ' ') TokenType.WHITE_SPACE else WORD_TOKEN
  }

  override fun getTokenStart(): Int = tokenStart
  override fun getTokenEnd(): Int = tokenEnd

  override fun advance() {
    tokenStart = tokenEnd
    tokenEnd = locateTokenEnd(tokenStart)
  }

  override fun getBufferSequence(): CharSequence = buffer
  override fun getBufferEnd(): Int = bufferEnd

  private fun locateTokenEnd(start: Int): Int {
    if (start >= bufferEnd) return bufferEnd
    val isSpace = buffer[start] == ' '
    var pos = start + 1
    while (pos < bufferEnd && (buffer[pos] == ' ') == isSpace) pos++
    return pos
  }
}

/**
 * Parser that wraps each word token in a WORD_ELEMENT node.
 * Whitespace tokens are automatically handled by the PsiBuilder based on [SpaceTokenizingParserDefinition.getWhitespaceTokens].
 */
private class SpaceTokenizingParser : PsiParser {
  override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
    val rootMarker = builder.mark()
    while (!builder.eof()) {
      val marker = builder.mark()
      builder.advanceLexer()
      marker.done(WORD_ELEMENT)
    }
    rootMarker.done(root)
    return builder.treeBuilt
  }
}

private class SpaceTokenizingParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?): com.intellij.lexer.Lexer = SpaceTokenizingLexer()
  override fun createParser(project: Project?): PsiParser = SpaceTokenizingParser()
  override fun getFileNodeType(): IFileElementType = FILE_ELEMENT_TYPE
  override fun getWhitespaceTokens(): TokenSet = TokenSet.create(TokenType.WHITE_SPACE)
  override fun getCommentTokens(): TokenSet = TokenSet.EMPTY
  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY
  override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)
  override fun createFile(viewProvider: FileViewProvider): PsiFile = SpaceTokenizingFile(viewProvider)
}
