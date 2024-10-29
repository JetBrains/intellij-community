// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.commandLine

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import java.io.File
import java.nio.charset.Charset


private val LOG = Logger.getInstance(CodeStyleProcessorBuilder::class.java)

class CodeStyleProcessorBuilder(private val messageOutput: MessageOutput) {
  private var isDryRun = false
  var isRecursive: Boolean = false
  private var primaryCodeStyle: CodeStyleSettings? = null
  private var defaultCodeStyle: CodeStyleSettings? = null
  private var fileMasks = emptyList<Regex>()
  val entries: ArrayList<File> = arrayListOf()
  var charset: Charset? = null

  fun dryRun(): CodeStyleProcessorBuilder = this.also { isDryRun = true }

  fun recursive(): CodeStyleProcessorBuilder = this.also { isRecursive = true }

  fun allowFactoryDefaults(): CodeStyleProcessorBuilder = this.also { defaultCodeStyle = CodeStyleSettingsManager.getInstance().createSettings() }

  fun withCodeStyleSettings(settings: CodeStyleSettings): CodeStyleProcessorBuilder = this.also { primaryCodeStyle = settings }

  fun withFileMasks(masks: String): CodeStyleProcessorBuilder = this.also {
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

  fun withEntry(entryPath: String): CodeStyleProcessorBuilder = this.also { entries.add(File(entryPath)) }

  fun withCharset(charset: Charset): CodeStyleProcessorBuilder = this.also { this.charset = charset }

  private fun FileSetCodeStyleProcessor.configure() = apply {
    fileMasks.forEach { mask ->
      LOG.info("File mask regexp: ${mask.pattern}")
      addFileMask(mask)
    }
    entries.forEach { file ->
      addEntry(file)
    }
  }

  private fun buildFormatter(project: Project) =
    FileSetFormatter(messageOutput, isRecursive, charset, primaryCodeStyle, defaultCodeStyle, project).configure()

  private fun buildFormatValidator(project: Project) =
    FileSetFormatValidator(messageOutput, isRecursive, charset, primaryCodeStyle, defaultCodeStyle, project).configure()

  fun build(project: Project): FileSetCodeStyleProcessor =
    if (isDryRun) buildFormatValidator(project) else buildFormatter(project)

}
