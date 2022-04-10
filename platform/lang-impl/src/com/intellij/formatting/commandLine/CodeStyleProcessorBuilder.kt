// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.commandLine

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import java.io.File
import java.nio.charset.Charset


private val LOG = Logger.getInstance(CodeStyleProcessorBuilder::class.java)

class CodeStyleProcessorBuilder(val messageOutput: MessageOutput) {
  var isDryRun = false
  var isRecursive = false
  var primaryCodeStyle: CodeStyleSettings? = null
  var defaultCodeStyle: CodeStyleSettings? = null
  var fileMasks = emptyList<Regex>()
  val entries = arrayListOf<File>()
  var charset: Charset? = null

  fun dryRun() = this.also { isDryRun = true }

  fun recursive() = this.also { isRecursive = true }

  fun allowFactoryDefaults() = this.also { defaultCodeStyle = CodeStyleSettingsManager.getInstance().createSettings() }

  fun withCodeStyleSettings(settings: CodeStyleSettings) = this.also { primaryCodeStyle = settings }

  fun withFileMasks(masks: String) = this.also {
    fileMasks = masks
      .split(",")
      .asSequence()
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .map { mask ->
        mask
          .replace(".", "\\.")
          .replace("*", ".*")
          .replace("?", ".")
          .replace("+", "\\+")
      }
      .map { pattern -> Regex(pattern) }
      .toList()
  }

  fun withEntry(entryPath: String) = this.also { entries.add(File(entryPath)) }

  fun withCharset(charset: Charset) = this.also { this.charset = charset }

  private fun FileSetCodeStyleProcessor.configure() = apply {
    fileMasks.forEach { mask ->
      LOG.info("File mask regexp: ${mask.pattern}")
      addFileMask(mask)
    }
    entries.forEach { file ->
      addEntry(file)
    }
  }

  private fun buildFormatter() =
    FileSetFormatter(messageOutput, isRecursive, charset, primaryCodeStyle, defaultCodeStyle).configure()

  private fun buildFormatValidator() =
    FileSetFormatValidator(messageOutput, isRecursive, charset, primaryCodeStyle, defaultCodeStyle).configure()

  fun build(): FileSetCodeStyleProcessor =
    if (isDryRun) buildFormatValidator() else buildFormatter()

}
