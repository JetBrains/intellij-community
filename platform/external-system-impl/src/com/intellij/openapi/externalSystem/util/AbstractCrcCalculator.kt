// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.util

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.ParserDefinition
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import java.util.zip.CRC32

abstract class AbstractCrcCalculator : ExternalSystemCrcCalculator {
  abstract fun isIgnoredToken(tokenType: IElementType, tokenText: CharSequence, parserDefinition: ParserDefinition): Boolean

  override fun calculateCrc(project: Project, file: VirtualFile, fileText: CharSequence): Long? {
    val parserDefinition = getParserDefinition(file.fileType) ?: return null
    return calculateCrc(project, fileText, parserDefinition)
  }

  private fun calculateCrc(project: Project,
                           charSequence: CharSequence,
                           parserDefinition: ParserDefinition): Long {
    val lexer = parserDefinition.createLexer(project)
    val crc32 = CRC32()
    lexer.start(charSequence)
    ProgressManager.checkCanceled()
    while (true) {
      val tokenType = lexer.tokenType ?: break
      val tokenText = charSequence.subSequence(lexer.tokenStart, lexer.tokenEnd)
      crc32.update(tokenType, tokenText, parserDefinition)
      lexer.advance()
      ProgressManager.checkCanceled()
    }
    return crc32.value
  }

  private fun CRC32.update(tokenType: IElementType,
                           tokenText: CharSequence,
                           parserDefinition: ParserDefinition) {
    if (isIgnoredToken(tokenType, tokenText, parserDefinition)) return
    if (tokenText.isBlank()) return
    update(tokenText)
  }

  private fun CRC32.update(charSequence: CharSequence) {
    update(charSequence.length)
    for (ch in charSequence) {
      update(ch.code)
    }
  }

  private fun getParserDefinition(fileType: FileType): ParserDefinition? {
    return when (fileType) {
      is LanguageFileType -> LanguageParserDefinitions.INSTANCE.forLanguage(fileType.language)
      else -> null
    }
  }
}