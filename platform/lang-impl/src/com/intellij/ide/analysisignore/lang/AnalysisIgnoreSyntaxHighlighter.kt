// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore.lang

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType

/**
 * Colors a comment of a `.analysisignore` file as a line comment. A pattern keeps the default text color.
 */
internal class AnalysisIgnoreSyntaxHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = AnalysisIgnoreLexer()

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
    return if (tokenType == AnalysisIgnoreElementTypes.COMMENT) pack(DefaultLanguageHighlighterColors.LINE_COMMENT)
    else TextAttributesKey.EMPTY_ARRAY
  }
}

internal class AnalysisIgnoreSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
  override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter = AnalysisIgnoreSyntaxHighlighter()
}
