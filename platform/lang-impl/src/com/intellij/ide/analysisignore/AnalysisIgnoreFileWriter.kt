// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.lang.LangBundle
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

/**
 * Writes the lines of a [`.analysisignore`][ANALYSIS_IGNORE_FILE_NAME] file that "Mark as Excluded" adds and "Cancel Exclusion" removes.
 */
@ApiStatus.Internal
object AnalysisIgnoreFileWriter {

  /**
   * Returns the directory whose `.analysisignore` file takes the line for [fileOrDir].
   */
  fun targetBaseDirOf(project: Project, fileOrDir: VirtualFile): VirtualFile? = runReadAction {
    val fileIndex = ProjectFileIndex.getInstance(project)
    val contentRoot = fileIndex.getContentRootForFile(fileOrDir, false) ?: return@runReadAction null
    var dir = fileOrDir.parent
    while (dir != null && fileIndex.isInContent(dir)) {
      if (dir.analysisIgnoreFile() != null) return@runReadAction dir
      dir = dir.parent
    }
    contentRoot
  }

  /**
   * Returns the line that names [fileOrDir] in a file of [baseDir], as `/build/` or `/gen/Foo.java`. Returns `null` when the format has
   * no such line.
   */
  fun literalLineOf(fileOrDir: VirtualFile, baseDir: VirtualFile): String? {
    val relativePath = VfsUtilCore.getRelativePath(fileOrDir, baseDir, '/')
    if (relativePath.isNullOrEmpty()) return null
    if (relativePath.any { it in WILDCARDS }) return null
    // The reader drops the trailing spaces of a line. A directory gets a `/` after its name, and thus only a file loses them.
    if (!fileOrDir.isDirectory && relativePath.endsWith(' ')) return null

    val line = if (fileOrDir.isDirectory) "/$relativePath/" else "/$relativePath"
    return line.takeIf { AnalysisIgnorePattern.validate(it) == AnalysisIgnoreValidated.Supported }
  }

  /**
   * Adds [line] to the `.analysisignore` file of [baseDir], and creates that file when there is none. Returns the file, or `null` when
   * the file system refuses.
   */
  @RequiresEdt
  fun appendLine(project: Project, baseDir: VirtualFile, line: String): VirtualFile? {
    var ignoreFile: VirtualFile? = null
    runCommand(project, LangBundle.message("analysis.ignore.command.exclude", ANALYSIS_IGNORE_FILE_NAME)) {
      val file = baseDir.analysisIgnoreFile() ?: createIgnoreFile(baseDir) ?: return@runCommand
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return@runCommand
      if (document.textLength > 0 && document.charsSequence[document.textLength - 1] != '\n') {
        document.insertString(document.textLength, "\n")
      }
      document.insertString(document.textLength, line + "\n")
      AnalysisIgnoreService.getInstance(project).applyInWriteAction(file)
      ignoreFile = file
    }
    ignoreFile?.let { save(it) }
    return ignoreFile
  }

  /**
   * Removes every line of [ignoreFile] whose pattern is [source]. Returns `true` when a line went away.
   */
  @RequiresEdt
  fun removeLines(project: Project, ignoreFile: VirtualFile, source: String): Boolean {
    var removed = false
    runCommand(project, LangBundle.message("analysis.ignore.command.cancel.exclusion", ANALYSIS_IGNORE_FILE_NAME)) {
      val document = FileDocumentManager.getInstance().getDocument(ignoreFile) ?: return@runCommand
      for (lineIndex in document.lineCount - 1 downTo 0) {
        val start = document.getLineStartOffset(lineIndex)
        val end = document.getLineEndOffset(lineIndex)
        if (AnalysisIgnorePattern.patternSource(document.getText(TextRange(start, end))) != source) continue
        // A document holds a `\n` alone between its lines. The last line may have none.
        document.deleteString(start, minOf(end + 1, document.textLength))
        removed = true
      }
      if (removed) {
        AnalysisIgnoreService.getInstance(project).applyInWriteAction(ignoreFile)
      }
    }
    if (removed) save(ignoreFile)
    return removed
  }

  /**
   * Returns the index of the first line of [text] whose pattern is [source], or `-1` when no line holds it.
   */
  fun lineIndexOf(text: CharSequence, source: String): Int {
    var index = 0
    for (line in text.lineSequence()) {
      if (AnalysisIgnorePattern.patternSource(line) == source) return index
      index++
    }
    return -1
  }

  private fun createIgnoreFile(baseDir: VirtualFile): VirtualFile? {
    return try {
      baseDir.createChildData(this, ANALYSIS_IGNORE_FILE_NAME)
    }
    catch (e: IOException) {
      LOG.warn("Cannot create $ANALYSIS_IGNORE_FILE_NAME in ${baseDir.presentableUrl}", e)
      null
    }
  }

  /** Saves the document of [ignoreFile] after the command, as a save takes a write action of its own. */
  private fun save(ignoreFile: VirtualFile) {
    val document: Document = FileDocumentManager.getInstance().getCachedDocument(ignoreFile) ?: return
    FileDocumentManager.getInstance().saveDocument(document)
  }

  private fun runCommand(project: Project, name: @NlsContexts.Command String, action: () -> Unit) {
    CommandProcessor.getInstance().executeCommand(project, { runUndoTransparentWriteAction(action) }, name, null)
  }

  private fun VirtualFile.analysisIgnoreFile(): VirtualFile? = findChild(ANALYSIS_IGNORE_FILE_NAME)?.takeUnless { it.isDirectory }

  private const val WILDCARDS: String = "*?"

  private val LOG = logger<AnalysisIgnoreFileWriter>()
}
