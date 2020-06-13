// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ignore

import com.intellij.CommonBundle
import com.intellij.ProjectTopics
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.actions.MarkExcludeRootAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FilesProcessorImpl
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileType
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.ui.SelectFilesDialog
import com.intellij.openapi.vcs.ignore.IgnoredToExcludedSynchronizerConstants.ASKED_MARK_IGNORED_FILES_AS_EXCLUDED_PROPERTY
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import java.util.*

private val LOG = logger<IgnoredToExcludedSynchronizer>()

private val excludeAction = object : MarkExcludeRootAction() {
  fun exclude(module: Module, dirs: Collection<VirtualFile>) = runInEdt { modifyRoots(module, dirs.toTypedArray()) }
}

@Service
class IgnoredToExcludedSynchronizer(project: Project) : VcsIgnoredHolderUpdateListener, FilesProcessorImpl(project, project) {

  override val askedBeforeProperty = ASKED_MARK_IGNORED_FILES_AS_EXCLUDED_PROPERTY
  override val doForCurrentProjectProperty: String? = null

  init {
    project.messageBus.connect(this).subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) = updateNotificationState()
    })
  }

  private fun updateNotificationState() {
    if (synchronizationTurnOff()) return

    // in case if the project roots changed (e.g. by build tools) then the directories shown in notification can be outdated.
    // filter directories which excluded or containing source roots and expire notification if needed.

    val fileIndex = ProjectFileIndex.getInstance(project)
    val sourceRoots = getProjectSourceRoots()

    val acquiredFiles = acquireValidFiles()
    LOG.debug("updateNotificationState, acquiredFiles", acquiredFiles)
    val filesToRemove = acquiredFiles
      .asSequence()
      .filter { file -> fileIndex.isExcluded(file) || sourceRoots.contains(file) }
      .toList()
    LOG.debug("updateNotificationState, filesToRemove", filesToRemove)

    if (removeFiles(filesToRemove)) {
      EditorNotifications.getInstance(project).updateAllNotifications()
    }
  }

  fun isNotEmpty() = !isFilesEmpty()

  fun clearFiles(files: Collection<VirtualFile>) = removeFiles(files)

  fun getValidFiles() = acquireValidFiles()

  fun muteForCurrentProject() {
    setForCurrentProject(false)
    PropertiesComponent.getInstance(project).setValue(askedBeforeProperty, true)
    clearFiles()
  }

  fun mutedForCurrentProject() = wasAskedBefore() && !needDoForCurrentProject()

  override fun doActionOnChosenFiles(files: Collection<VirtualFile>) {
    if (files.isEmpty()) return

    markIgnoredAsExcluded(files)
  }

  override fun doFilterFiles(files: Collection<VirtualFile>) = files.filter(VirtualFile::isValid)

  override fun rememberForAllProjects() {}

  override fun rememberForCurrentProject() {
    VcsConfiguration.getInstance(project).MARK_IGNORED_AS_EXCLUDED = true
  }

  override fun needDoForCurrentProject() = VcsConfiguration.getInstance(project).MARK_IGNORED_AS_EXCLUDED

  override fun updateFinished(ignoredPaths: Collection<FilePath>, isFullRescan: Boolean) {
    ProgressManager.checkCanceled()
    if (synchronizationTurnOff()) return
    if (!isFullRescan) return
    if (!VcsConfiguration.getInstance(project).MARK_IGNORED_AS_EXCLUDED && wasAskedBefore()) return

    processIgnored(ignoredPaths)
  }

  private fun processIgnored(ignoredPaths: Collection<FilePath>) {
    val sourceRoots = getProjectSourceRoots()
    val fileIndex = ProjectFileIndex.getInstance(project)
    val ignoredDirs =
      ignoredPaths
        .asSequence()
        .filter(FilePath::isDirectory)
        //shelf directory usually contains in project and excluding it prevents local history to work on it
        .filterNot(::containsShelfDirectoryOrUnderIt)
        .mapNotNull(FilePath::getVirtualFile)
        .filterNot { runReadAction { fileIndex.isExcluded(it) } }
        //do not propose to exclude if there is a source root inside
        .filterNot { ignored -> sourceRoots.contains(ignored) }
        .toList()

    if (allowShowNotification()) {
      clearFiles()
      processFiles(ignoredDirs)
      val editorNotifications = EditorNotifications.getInstance(project)
      FileEditorManager.getInstance(project).openFiles
        .forEach { openFile ->
          if (openFile.fileType is IgnoreFileType) {
            editorNotifications.updateNotifications(openFile)
          }
        }
    }
    else if (needDoForCurrentProject()) {
      doActionOnChosenFiles(doFilterFiles(ignoredDirs))
    }
  }

  fun markIgnoredAsExcluded(files: Collection<VirtualFile>) {
    val ignoredDirsByModule =
      files
        .asSequence()
        .groupBy { ModuleUtil.findModuleForFile(it, project) }
        //if the directory already excluded then ModuleUtil.findModuleForFile return null and this will filter out such directories from processing.
        .filterKeys(Objects::nonNull)

    for ((module, ignoredDirs) in ignoredDirsByModule) {
      excludeAction.exclude(module!!, ignoredDirs)
    }
  }

  private fun getProjectSourceRoots() =
    runReadAction { OrderEnumerator.orderEntries(project).withoutSdk().withoutLibraries().sources().usingCache().roots }
  private fun containsShelfDirectoryOrUnderIt(filePath: FilePath) =
    FileUtil.isAncestor(ShelveChangesManager.getShelfPath(project), filePath.path, false)
    || FileUtil.isAncestor(filePath.path, ShelveChangesManager.getShelfPath(project), false)

}

private fun allowShowNotification() = Registry.`is`("vcs.propose.add.ignored.directories.to.exclude", true)
private fun synchronizationTurnOff() = !Registry.`is`("vcs.enable.add.ignored.directories.to.exclude", true)

class IgnoredToExcludeNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {

  companion object {
    private val KEY: Key<EditorNotificationPanel> = Key.create("IgnoredToExcludeNotificationProvider")
  }

  override fun getKey(): Key<EditorNotificationPanel> = KEY

  private fun canCreateNotification(project: Project, file: VirtualFile) =
    file.fileType is IgnoreFileType &&
    with(project.service<IgnoredToExcludedSynchronizer>()) {
      !synchronizationTurnOff() &&
      allowShowNotification() &&
      !mutedForCurrentProject() &&
      isNotEmpty()
    }

  private fun showIgnoredAction(project: Project) {
    val allFiles = project.service<IgnoredToExcludedSynchronizer>().getValidFiles()
    if (allFiles.isEmpty()) return

    val dialog = IgnoredToExcludeSelectDirectoriesDialog(project, allFiles)
    if (!dialog.showAndGet()) return

    val userSelectedFiles = dialog.selectedFiles
    if (userSelectedFiles.isEmpty()) return

    with(project.service<IgnoredToExcludedSynchronizer>()) {
      markIgnoredAsExcluded(userSelectedFiles)
      clearFiles(userSelectedFiles)
    }
  }

  private fun muteAction(project: Project) = Runnable {
    project.service<IgnoredToExcludedSynchronizer>().muteForCurrentProject()
    EditorNotifications.getInstance(project).updateNotifications(this@IgnoredToExcludeNotificationProvider)
  }

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    if (!canCreateNotification(project, file)) return null

    return EditorNotificationPanel().apply {
      icon(AllIcons.General.Information)
      text = message("ignore.to.exclude.notification.message")
      createActionLabel(message("ignore.to.exclude.notification.action.view")) { showIgnoredAction(project) }
      createActionLabel(message("ignore.to.exclude.notification.action.mute"), muteAction(project))
    }
  }
}

// do not use SelectFilesDialog.init because it doesn't provide clear statistic: what exactly dialog shown/closed, action clicked
private class IgnoredToExcludeSelectDirectoriesDialog(
  project: Project?,
  files: List<VirtualFile>
) : SelectFilesDialog(project, files, message("ignore.to.exclude.notification.notice"), null, true, true) {
  init {
    title = message("ignore.to.exclude.view.dialog.title")
    selectedFiles = files
    setOKButtonText(message("ignore.to.exclude.view.dialog.exclude.action"))
    setCancelButtonText(CommonBundle.getCancelButtonText())
    init()
  }
}
