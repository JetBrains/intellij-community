// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.commandLine

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathEvaluationResult
import javax.xml.xpath.XPathFactory


private var LOG = Logger.getInstance(FileSetProcessor::class.java)


abstract class FileSetProcessor(
  val messageOutput: MessageOutput,
  val isRecursive: Boolean,
  val charset: Charset? = null
) {

  private val topEntries = arrayListOf<File>()
  private val fileMasks = arrayListOf<Regex>()

  protected val statistics = FileSetProcessingStatistics()

  val total: Int
    get() = statistics.getTotal()

  val processed: Int
    get() = statistics.getProcessed()

  val succeeded: Int
    get() = statistics.getValid()

  fun addEntry(filePath: String) = addEntry(File(filePath))

  fun addEntry(file: File) =
    file
      .takeIf { it.exists() }
      ?.let { topEntries.add(it) }
    ?: throw IOException("File $file not found.")

  fun addFileMask(mask: Regex) = fileMasks.add(mask)

  private fun File.matchesFileMask() =
    fileMasks.isEmpty() || fileMasks.any { mask -> mask.matches(name) }

  private fun File.toVirtualFile() =
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(this) ?: throw IOException("Can not find $path")

  fun processFiles() = topEntries.forEach { entry ->

    val outerProjectSettings = findCodeStyleSettings(entry.getOuterProject())

    var innerProjectSettings: CodeStyleSettings? = null
    var currentProject: File? = null

    entry
      .walkTopDown()
      .maxDepth(if (isRecursive) Int.MAX_VALUE else 1)
      .onEnter { dir ->
        if (outerProjectSettings == null && innerProjectSettings == null) {
          val dotIdea = dir.resolve(".idea")
          innerProjectSettings = findCodeStyleSettings(dotIdea)
            ?.also {
              currentProject = dir
              LOG.info("Switching to project specific settings for ${dir.path}")
            }

        }
        LOG.info("Scanning directory ${dir.path}")
        true
      }
      .onLeave {
        if (it == currentProject) {
          currentProject = null
          innerProjectSettings = null
        }
      }
      .filter { it.isFile }
      .filter { it.matchesFileMask() }
      .map { ioFile -> ioFile.toVirtualFile() }
      .onEach { vFile -> charset?.let { vFile.charset = it } }
      .forEach { vFile ->
        LOG.info("Processing ${vFile.path}")
        statistics.fileTraversed()
        processVirtualFile(vFile, outerProjectSettings ?: innerProjectSettings)
      }
  }

  abstract fun processVirtualFile(virtualFile: VirtualFile, projectSettings: CodeStyleSettings?)

  fun getFileMasks() = fileMasks.toList()
  fun getEntries() = topEntries.toList()

}

// Finds nearest enclosing project contains this file
private tailrec fun File.getOuterProject(): File? {
  val parent: File = absoluteFile.parentFile ?: return null
  val dotIdea = parent.resolve(".idea")
  if (dotIdea.isDirectory) return dotIdea
  return parent.getOuterProject()
}

private val documentBuilderFactory = DocumentBuilderFactory.newInstance()
private val xPathFactory = XPathFactory.newInstance()
private val usePerProjectSelector = "/component[@name='ProjectCodeStyleConfiguration']/state/option[@name='USE_PER_PROJECT_SETTINGS']/@value='true'"
private val usePerProjectXPath = xPathFactory.newXPath().compile(usePerProjectSelector)

private fun findCodeStyleSettings(dotIdea: File?): CodeStyleSettings? {
  if (dotIdea == null) return null
  if (!dotIdea.isDirectory) return null

  val codeStyles = dotIdea.resolve("codeStyles")
  if (!codeStyles.isDirectory) return null

  val codeStyleConfig = codeStyles.resolve("codeStyleConfig.xml")
  if (!codeStyleConfig.isFile) return null

  val doc = documentBuilderFactory
    .newDocumentBuilder()
    .parse(codeStyleConfig)

  usePerProjectXPath
    .evaluateExpression(doc)
    .takeIf { it.type() == XPathEvaluationResult.XPathResultType.BOOLEAN }
    ?.takeIf { it.value() == true }
  ?: return null

  return codeStyles
    .resolve("Project.xml")
    .takeIf { it.isFile }
    ?.let {
      readSettings(it)
    }

}
