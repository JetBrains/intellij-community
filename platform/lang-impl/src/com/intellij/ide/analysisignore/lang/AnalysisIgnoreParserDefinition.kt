// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore.lang

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/**
 * Builds the PSI of a `.analysisignore` file: one [AnalysisIgnoreElementTypes.ENTRY] per pattern, and a comment leaf per comment.
 */
internal class AnalysisIgnoreParserDefinition : ParserDefinition {
  override fun createLexer(project: Project?): Lexer = AnalysisIgnoreLexer()

  override fun createParser(project: Project?): PsiParser = PsiParser { root, builder ->
    val rootMarker = builder.mark()
    // The builder skips the white space and the comments, so each token here is a pattern.
    while (!builder.eof()) {
      val entry = builder.mark()
      builder.advanceLexer()
      entry.done(AnalysisIgnoreElementTypes.ENTRY)
    }
    rootMarker.done(root)
    builder.treeBuilt
  }

  override fun getFileNodeType(): IFileElementType = AnalysisIgnoreElementTypes.FILE

  override fun getCommentTokens(): TokenSet = AnalysisIgnoreElementTypes.COMMENTS

  override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

  override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)

  override fun createFile(viewProvider: FileViewProvider): PsiFile = AnalysisIgnorePsiFile(viewProvider)
}

internal class AnalysisIgnorePsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, AnalysisIgnoreLanguage) {
  override fun getFileType(): FileType = AnalysisIgnoreFileType
}
