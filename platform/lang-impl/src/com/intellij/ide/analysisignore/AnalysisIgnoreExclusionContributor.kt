// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.lang.LangBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.workspaceModel.ide.OptionalExclusionContributor

/**
 * Routes "Mark as Excluded" and "Cancel Exclusion" to the [`.analysisignore`][ANALYSIS_IGNORE_FILE_NAME] files. "Mark as Excluded" adds
 * a line that names the path, and "Cancel Exclusion" removes that line.
 */
internal class AnalysisIgnoreExclusionContributor : OptionalExclusionContributor {

  override fun requestExclusion(project: Project, fileOrDir: VirtualFile): Boolean {
    if (!isEnabled()) return false
    val baseDir = AnalysisIgnoreFileWriter.targetBaseDirOf(project, fileOrDir) ?: return false
    val line = AnalysisIgnoreFileWriter.literalLineOf(fileOrDir, baseDir) ?: return false

    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) {
      return AnalysisIgnoreFileWriter.appendLine(project, baseDir, line) != null
    }
    application.invokeLater({ AnalysisIgnoreFileWriter.appendLine(project, baseDir, line) }, project.disposed)
    return true
  }

  override fun canCancelExclusion(project: Project, excludedFileOrDir: VirtualFile): Boolean {
    return isEnabled() && findAnalysisIgnoreExclusions(project, excludedFileOrDir).isNotEmpty()
  }

  override fun requestExclusionCancellation(project: Project, excludedFileOrDir: VirtualFile): Boolean {
    if (!isEnabled()) return false
    val exclusions = findAnalysisIgnoreExclusions(project, excludedFileOrDir)
    if (exclusions.isEmpty()) return false

    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) {
      cancelExclusion(project, excludedFileOrDir, exclusions)
    }
    else {
      application.invokeLater({ cancelExclusion(project, excludedFileOrDir, exclusions) }, project.disposed)
    }
    return true
  }

  private fun isEnabled(): Boolean = Registry.`is`(ANALYSIS_IGNORE_ENABLED_KEY, true)
}

/**
 * Removes every line that names [fileOrDir] itself. When a line of a directory above [fileOrDir], or a wildcard, still excludes it, opens
 * the first such line after the action, because the user has to edit that line.
 */
@RequiresEdt
private fun cancelExclusion(project: Project, fileOrDir: VirtualFile, exclusions: List<AnalysisIgnoreExclusion>) {
  val remaining = ArrayList<AnalysisIgnoreExclusion>()
  for (exclusion in exclusions) {
    val ignoreFile = exclusion.ignoreFile ?: continue
    if (exclusion.excludedFile == fileOrDir && exclusion.pattern.literalPath != null) {
      AnalysisIgnoreFileWriter.removeLines(project, ignoreFile, exclusion.pattern.source)
    }
    else {
      remaining.add(exclusion)
    }
  }
  val responsible = remaining.firstOrNull() ?: return
  ApplicationManager.getApplication().invokeLater({ showResponsibleLine(project, fileOrDir, responsible) }, project.disposed)
}

/**
 * Opens the `.analysisignore` file of [exclusion] with its line selected, and tells in a balloon why [fileOrDir] stays excluded.
 */
@RequiresEdt
private fun showResponsibleLine(project: Project, fileOrDir: VirtualFile, exclusion: AnalysisIgnoreExclusion) {
  val ignoreFile = exclusion.ignoreFile ?: return
  val source = exclusion.pattern.source
  val document = FileDocumentManager.getInstance().getDocument(ignoreFile)
  val lineIndex = if (document == null) -1 else AnalysisIgnoreFileWriter.lineIndexOf(document.charsSequence, source)

  val descriptor = OpenFileDescriptor(project, ignoreFile, maxOf(lineIndex, 0), 0)
  val editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
  if (editor != null && document != null && lineIndex >= 0) {
    val start = document.getLineStartOffset(lineIndex)
    editor.selectionModel.setSelection(start, start + source.length)
  }

  NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
    .createNotification(
      LangBundle.message("analysis.ignore.notification.excluded.by.line.title", ANALYSIS_IGNORE_FILE_NAME),
      LangBundle.message("analysis.ignore.notification.excluded.by.line.content", fileOrDir.name, source, ignoreFile.presentableUrl),
      NotificationType.INFORMATION,
    )
    .notify(project)
}

private const val NOTIFICATION_GROUP_ID: String = "Analysis ignore"
