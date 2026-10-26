// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.workspaceModel.ide.OptionalExclusionContributor

/**
 * Routes "Mark as Excluded" and "Cancel Exclusion" to the [`.analysisignore`][ANALYSIS_IGNORE_FILE_NAME] files. "Mark as Excluded" adds
 * a line that names the path. "Cancel Exclusion" opens the line that excludes the path and selects it, so that the user edits or removes
 * it. For a path that the [defaults][AnalysisIgnoreDefaults] of a project root exclude, that line is on the
 * [Default Exclusions][AnalysisIgnoreDefaultsConfigurable] page of the settings.
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
    return isEnabled() &&
           (findAnalysisIgnoreExclusions(project, excludedFileOrDir).isNotEmpty() ||
            findDefaultExclusions(project, excludedFileOrDir).isNotEmpty())
  }

  override fun requestExclusionCancellation(project: Project, excludedFileOrDir: VirtualFile): Boolean {
    if (!isEnabled()) return false
    val exclusions = findAnalysisIgnoreExclusions(project, excludedFileOrDir)
    val defaultExclusions = findDefaultExclusions(project, excludedFileOrDir)
    if (exclusions.isEmpty() && defaultExclusions.isEmpty()) return false

    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) {
      cancelExclusion(project, exclusions, defaultExclusions)
    }
    else {
      application.invokeLater({ cancelExclusion(project, exclusions, defaultExclusions) }, project.disposed)
    }
    return true
  }

  private fun isEnabled(): Boolean = Registry.`is`(ANALYSIS_IGNORE_ENABLED_KEY, true)
}

/**
 * Shows the first line of [exclusions], or else of [defaultExclusions], after the action, and selects it. Thus the user edits or removes
 * the line that excludes the item. The line of a `.analysisignore` file comes first, and its file opens in the editor. Without one, the
 * line is a default line, and the [Default Exclusions][AnalysisIgnoreDefaultsConfigurable] page opens with that line highlighted.
 * The method changes no line itself.
 */
@RequiresEdt
private fun cancelExclusion(
  project: Project,
  exclusions: List<AnalysisIgnoreExclusion>,
  defaultExclusions: List<AnalysisIgnoreExclusion>,
) {
  val exclusion = exclusions.firstOrNull { it.ignoreFile != null }
  val source = (exclusion ?: defaultExclusions.firstOrNull() ?: return).pattern.source
  ApplicationManager.getApplication().invokeLater({
    val ignoreFile = exclusion?.ignoreFile
    if (ignoreFile != null) {
      showLine(project, ignoreFile, source)
    }
    else {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, AnalysisIgnoreDefaultsConfigurable::class.java) {
        it.selectDefaultExclusion(source)
      }
    }
  }, project.disposed)
}

/** Opens [ignoreFile] with its line of [source] selected. */
@RequiresEdt
private fun showLine(project: Project, ignoreFile: VirtualFile, source: String) {
  if (!ignoreFile.isValid) return
  val document = FileDocumentManager.getInstance().getDocument(ignoreFile)
  val lineIndex = if (document == null) -1 else AnalysisIgnoreFileWriter.lineIndexOf(document.charsSequence, source)

  val descriptor = OpenFileDescriptor(project, ignoreFile, maxOf(lineIndex, 0), 0)
  val editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
  if (editor != null && document != null && lineIndex >= 0) {
    val start = document.getLineStartOffset(lineIndex)
    editor.selectionModel.setSelection(start, start + source.length)
  }
}
