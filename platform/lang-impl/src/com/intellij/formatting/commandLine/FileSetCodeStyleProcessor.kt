// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.commandLine

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.service.CoreFormattingService
import com.intellij.formatting.service.FormattingServiceUtil
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.lang.LanguageFormatting
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.util.LocalTimeCounter
import com.intellij.util.PlatformUtils
import org.jetbrains.jps.model.serialization.PathMacroUtil
import java.io.Closeable
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.*


private val LOG = Logger.getInstance(FileSetCodeStyleProcessor::class.java)


private const val RESULT_MESSAGE_OK = "OK"
private const val RESULT_MESSAGE_FAILED = "Failed"
private const val RESULT_MESSAGE_NOT_SUPPORTED = "Skipped, not supported."
private const val RESULT_MESSAGE_REJECTED_BY_FORMATTER = "Skipped, rejected by formatter."
private const val RESULT_MESSAGE_BINARY_FILE = "Skipped, binary file."

private const val RESULT_MESSAGE_DRY_OK = "Formatted well"
private const val RESULT_MESSAGE_DRY_FAIL = "Needs reformatting"


class FileSetFormatter(
  messageOutput: MessageOutput,
  isRecursive: Boolean,
  charset: Charset? = null,
  primaryCodeStyle: CodeStyleSettings? = null,
  defaultCodeStyle: CodeStyleSettings? = null
) : FileSetCodeStyleProcessor(messageOutput, isRecursive, charset, primaryCodeStyle, defaultCodeStyle) {

  override val operationContinuous = "Formatting"
  override val operationPerfect = "formatted"

  override fun processFileInternal(virtualFile: VirtualFile): String {
    val document = FileDocumentManager.getInstance().getDocument(virtualFile)
    if (document == null) {
      LOG.warn("No document available for " + virtualFile.path)
      return RESULT_MESSAGE_FAILED
    }

    try {
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
      NonProjectFileWritingAccessProvider.allowWriting(listOf(virtualFile))
      if (psiFile == null) {
        LOG.warn("Unable to get a PSI file for " + virtualFile.path)
        return RESULT_MESSAGE_FAILED
      }

      if (!psiFile.isFormattingSupported()) {
        return RESULT_MESSAGE_NOT_SUPPORTED
      }

      try {
        reformatFile(psiFile, document)
      }
      catch (pce: ProcessCanceledException) {
        val cause = pce.cause?.message ?: pce.message ?: ""
        LOG.warn("${virtualFile.canonicalPath}: $RESULT_MESSAGE_REJECTED_BY_FORMATTER $cause")
        return RESULT_MESSAGE_REJECTED_BY_FORMATTER
      }
      FileDocumentManager.getInstance().saveDocument(document)
    }
    finally {
      closeOpenFiles()
    }

    statistics.fileProcessed(true)
    return RESULT_MESSAGE_OK
  }

  private fun reformatFile(file: PsiFile, document: Document) {
    WriteCommandAction.runWriteCommandAction(project) {
      val codeStyleManager = CodeStyleManager.getInstance(project)
      codeStyleManager.reformatText(file, 0, file.textLength)
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
  }

  private fun closeOpenFiles() {
    val editorManager = FileEditorManager.getInstance(project)
    val openFiles = editorManager.openFiles
    for (openFile in openFiles) {
      editorManager.closeFile(openFile)
    }
  }

}


class FileSetFormatValidator(
  messageOutput: MessageOutput,
  isRecursive: Boolean,
  charset: Charset? = null,
  primaryCodeStyle: CodeStyleSettings? = null,
  defaultCodeStyle: CodeStyleSettings? = null
) : FileSetCodeStyleProcessor(messageOutput, isRecursive, charset, primaryCodeStyle, defaultCodeStyle) {

  override val operationContinuous = "Checking"
  override val operationPerfect = "checked"

  override fun printReport() {
    super.printReport()
    messageOutput.info("${succeeded} file(s) are well formed.\n")
  }

  override fun isResultSuccessful() = succeeded == processed

  override fun processFileInternal(virtualFile: VirtualFile): String {
    val document = FileDocumentManager.getInstance().getDocument(virtualFile)
    if (document == null) {
      LOG.warn("No document available for " + virtualFile.path)
      return RESULT_MESSAGE_FAILED
    }
    val originalContent = document.text

    val psiCopy = createPsiCopy(virtualFile, originalContent)

    CodeStyleManager
      .getInstance(project)
      .reformatText(psiCopy, 0, psiCopy.textLength)

    val reformattedContent = psiCopy.text

    return if (originalContent == reformattedContent) {
      statistics.fileProcessed(true)
      RESULT_MESSAGE_DRY_OK
    }
    else {
      statistics.fileProcessed(false)
      RESULT_MESSAGE_DRY_FAIL
    }
  }

  private fun createPsiCopy(originalFile: VirtualFile, originalContent: String): PsiFile {
    val psiCopy = PsiFileFactory.getInstance(project).createFileFromText(
      "a." + originalFile.fileType.defaultExtension,
      originalFile.fileType,
      originalContent,
      LocalTimeCounter.currentTime(),
      false
    )

    val originalPsi = PsiManager.getInstance(project).findFile(originalFile)
    if (originalPsi != null) {
      psiCopy.putUserData(PsiFileFactory.ORIGINAL_FILE, originalPsi)
    }

    return psiCopy
  }

}

abstract class FileSetCodeStyleProcessor(
  messageOutput: MessageOutput,
  isRecursive: Boolean,
  charset: Charset? = null,
  val primaryCodeStyle: CodeStyleSettings? = null,
  val defaultCodeStyle: CodeStyleSettings? = null
) : FileSetProcessor(messageOutput, isRecursive, charset), Closeable {

  private val projectUID = UUID.randomUUID().toString()
  protected val project = createProject(projectUID)

  abstract val operationContinuous: String
  abstract val operationPerfect: String

  abstract fun processFileInternal(virtualFile: VirtualFile): String

  open fun printReport() {
    messageOutput.info("\n")
    messageOutput.info("${total} file(s) scanned.\n")
    messageOutput.info("${processed} file(s) $operationPerfect.\n")
  }

  open fun isResultSuccessful() = true

  override fun close() {
    ProjectManagerEx.getInstanceEx().closeAndDispose(project)
  }

  override fun processVirtualFile(virtualFile: VirtualFile, projectSettings: CodeStyleSettings?) {
    messageOutput.info("$operationContinuous ${virtualFile.canonicalPath}...")

    val style = listOfNotNull(primaryCodeStyle, projectSettings, defaultCodeStyle).firstOrNull()

    if (style == null) {
      messageOutput.error("No style for ${virtualFile.canonicalPath}, skipping...")
      statistics.fileProcessed(true)
      return
    }

    withStyleSettings(style) {
      VfsUtil.markDirtyAndRefresh(false, false, false, virtualFile)
      val resultMessage =
        if (virtualFile.fileType.isBinary) {
          RESULT_MESSAGE_BINARY_FILE
        }
        else {
          processFileInternal(virtualFile)
        }
      messageOutput.info("$resultMessage\n")
    }
  }

  private fun <T> withStyleSettings(style: CodeStyleSettings, body: () -> T): T {
    val cssManager = CodeStyleSettingsManager.getInstance(project)
    val tmp = cssManager.mainProjectCodeStyle!!
    try {
      CodeStyle.setMainProjectSettings(project, style)
      return body()
    } finally {
      CodeStyle.setMainProjectSettings(project, tmp)
    }
  }

}


private val PROJECT_DIR_PREFIX = PlatformUtils.getPlatformPrefix() + ".format."
private const val PROJECT_DIR_SUFFIX = ".tmp"

private fun createProjectDir(projectUID: String) = FileUtil
  .createTempDirectory(PROJECT_DIR_PREFIX, projectUID + PROJECT_DIR_SUFFIX)
  .toPath()
  .resolve(PathMacroUtil.DIRECTORY_STORE_NAME)
  .also { Files.createDirectories(it) }

private fun createProject(projectUID: String) =
  ProjectManagerEx.getInstanceEx()
    .openProject(createProjectDir(projectUID), OpenProjectTask(isNewProject = true))
    ?.also {
      CodeStyle.setMainProjectSettings(it, CodeStyleSettingsManager.getInstance().createSettings())
    }
  ?: throw RuntimeException("Failed to create temporary project $projectUID")

private fun PsiFile.isFormattingSupported(): Boolean {
  val formattingService = FormattingServiceUtil.findService(this, true, true)
  return (formattingService !is CoreFormattingService)
         || (LanguageFormatting.INSTANCE.forContext(this) != null)
}
