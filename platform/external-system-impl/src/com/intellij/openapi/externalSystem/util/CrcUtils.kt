// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("CrcUtils")

package com.intellij.openapi.externalSystem.util

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.ParserDefinition
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemSettingsFileCrcCalculator
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import java.util.zip.CRC32

fun Document.calculateCrc(project: Project, file: VirtualFile): Long = this.calculateCrc(project, null, file)

fun Document.calculateCrc(project: Project, systemId: ProjectSystemId?, file: VirtualFile): Long {
  file.getCachedCrc(modificationStamp)?.let { return it }
  return findOrCalculateCrc(modificationStamp) {
    doCalculateCrc(project, systemId, file)
  }
}

fun VirtualFile.calculateCrc(project: Project) = calculateCrc(project, null)

fun VirtualFile.calculateCrc(project: Project, systemId: ProjectSystemId?) =
  findOrCalculateCrc(modificationStamp) {
    doCalculateCrc(project, systemId)
  }

private fun <T : UserDataHolder> T.findOrCalculateCrc(modificationStamp: Long, calculate: () -> Long?): Long {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  val cachedCrc = getCachedCrc(modificationStamp)
  if (cachedCrc != null) return cachedCrc
  val crc = calculate() ?: modificationStamp
  setCachedCrc(crc, modificationStamp)
  return crc
}

private fun doCalculateCrc(project: Project, charSequence: CharSequence, systemId: ProjectSystemId?, file: VirtualFile): Long? {
  if (systemId != null) {
    val crcCalculator = ExternalSystemSettingsFileCrcCalculator.getInstance(systemId, file)
    if (crcCalculator != null) {
      return crcCalculator.calculateCrc(project, file, charSequence)
    }
  }
  val parserDefinition = getParserDefinition(file.fileType) ?: return null
  val ignoredTokens = TokenSet.orSet(parserDefinition.commentTokens, parserDefinition.whitespaceTokens)
  return calculateCrc(project, charSequence, parserDefinition) { tokenType, _ -> ignoredTokens.contains(tokenType) }
}

fun calculateCrc(project: Project,
                 charSequence: CharSequence,
                 parserDefinition: ParserDefinition,
                 ignoreToken: (IElementType, CharSequence) -> Boolean): Long {
  val lexer = parserDefinition.createLexer(project)
  val crc32 = CRC32()
  lexer.start(charSequence)
  ProgressManager.checkCanceled()
  while (true) {
    val tokenType = lexer.tokenType ?: break
    val tokenText = charSequence.subSequence(lexer.tokenStart, lexer.tokenEnd)
    crc32.update(tokenType, tokenText, ignoreToken)
    lexer.advance()
    ProgressManager.checkCanceled()
  }
  return crc32.value
}

private fun CRC32.update(tokenType: IElementType, tokenText: CharSequence, ignoreToken: (IElementType, CharSequence) -> Boolean) {
  if (ignoreToken(tokenType, tokenText)) return
  if (tokenText.isBlank()) return
  update(tokenText)
}

private fun CRC32.update(charSequence: CharSequence) {
  update(charSequence.length)
  for (ch in charSequence) {
    update(ch.toInt())
  }
}

private fun getParserDefinition(fileType: FileType): ParserDefinition? {
  return when (fileType) {
    is LanguageFileType -> LanguageParserDefinitions.INSTANCE.forLanguage(fileType.language)
    else -> null
  }
}

private fun Document.doCalculateCrc(project: Project, systemId: ProjectSystemId?, file: VirtualFile) =
  when {
    file.fileType.isBinary -> null
    else -> doCalculateCrc(project, immutableCharSequence, systemId, file)
  }

private fun VirtualFile.doCalculateCrc(project: Project, systemId: ProjectSystemId?) =
  when {
    isDirectory -> null
    fileType.isBinary -> null
    else -> doCalculateCrc(project, LoadTextUtil.loadText(this), systemId, this)
  }

private fun UserDataHolder.getCachedCrc(modificationStamp: Long): Long? {
  val (value, stamp) = getUserData(CRC_CACHE) ?: return null
  if (stamp == modificationStamp) return value
  return null
}

private fun UserDataHolder.setCachedCrc(value: Long, modificationStamp: Long) {
  putUserData(CRC_CACHE, CrcCache(value, modificationStamp))
}

private val CRC_CACHE = Key<CrcCache>("com.intellij.openapi.externalSystem.util.CRC_CACHE")

private data class CrcCache(val value: Long, val modificationStamp: Long)
