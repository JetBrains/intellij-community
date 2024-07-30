// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ignore

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.projectView.actions.MarkExcludeRootAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
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
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FilesProcessorImpl
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsNotificationIdsHolder.Companion.IGNORED_TO_EXCLUDE_NOT_FOUND
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.VcsIgnoreManagerImpl
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileType
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.ui.SelectFilesDialog
import com.intellij.openapi.vcs.ignore.IgnoredToExcludedSynchronizerConstants.ASKED_MARK_IGNORED_FILES_AS_EXCLUDED_PROPERTY
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.util.Alarm
import com.intellij.util.application
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.function.Function
import javax.swing.JComponent

private val LOG = logger<IgnoredToExcludedSynchronizer>()

private val excludeAction = object : MarkExcludeRootAction() {
  fun exclude(module: Module, dirs: Collection<VirtualFile>) = runInEdt { modifyRoots(module, dirs.toTypedArray()) }
}

/**
 * Shows [EditorNotifications] in .ignore files with suggestion to exclude ignored directories.
 * Silently excludes them if [VcsConfiguration.MARK_IGNORED_AS_EXCLUDED] is enabled.
 *
 * Not internal service. Can be used directly in related modules.
 */
@Service(Service.Level.PROJECT)
class IgnoredToExcludedSynchronizer(project: Project, cs: CoroutineScope) : FilesProcessorImpl(project, project) {
  private val queue = MergingUpdateQueue("IgnoredToExcludedSynchronizer", 1000, true, null, this, null, Alarm.ThreadToUse.POOLED_THREAD)

  init {
    cs.launch {
      WorkspaceModel.getInstance(project).eventLog.collect { event ->
        // listen content roots, source roots, excluded roots
        if (event.getChanges(ContentRootEntity::class.java).isNotEmpty() ||
            event.getChanges(SourceRootEntity::class.java).isNotEmpty()) {
          updateNotificationState()
        }
      }
    }
  }

  /**
   * In case if the project roots changed (e.g. by build tools) then the directories shown in notification can be outdated.
   * Filter directories which excluded or containing source roots and expire notification if needed.
   */
  private fun updateNotificationState() {
    if (synchronizationTurnOff()) return
    if (isFilesEmpty()) return

    queue.queue(Update.create("update") {
      val fileIndex = ProjectFileIndex.getInstance(project)
      val sourceRoots = getProjectSourceRoots(project)

      val acquiredFiles = selectValidFiles()
      LOG.debug("updateNotificationState, acquiredFiles", acquiredFiles)
      val filesToRemove = acquiredFiles
        .asSequence()
        .filter { file -> runReadAction { fileIndex.isExcluded(file) } || sourceRoots.contains(file) }
        .toList()
      LOG.debug("updateNotificationState, filesToRemove", filesToRemove)

      if (removeFiles(filesToRemove)) {
        EditorNotifications.getInstance(project).updateAllNotifications()
      }
    })
  }

  fun isNotEmpty() = !isFilesEmpty()

  fun clearFiles(files: Collection<VirtualFile>) = removeFiles(files)

  fun getValidFiles() = with(ChangeListManager.getInstance(project)) { selectValidFiles().filter(this::isIgnoredFile) }

  private fun wasAskedBefore() = PropertiesComponent.getInstance(project).getBoolean(ASKED_MARK_IGNORED_FILES_AS_EXCLUDED_PROPERTY, false)

  fun muteForCurrentProject() {
    PropertiesComponent.getInstance(project).setValue(ASKED_MARK_IGNORED_FILES_AS_EXCLUDED_PROPERTY, true)
    clearFiles()
  }

  fun mutedForCurrentProject() = wasAskedBefore() && !needDoForCurrentProject()

  override fun doActionOnChosenFiles(files: Collection<VirtualFile>) {
    if (files.isEmpty()) return

    markIgnoredAsExcluded(project, files)
  }

  override fun doFilterFiles(files: Collection<VirtualFile>) = files.filter(VirtualFile::isValid)

  override fun needDoForCurrentProject() = VcsConfiguration.getInstance(project).MARK_IGNORED_AS_EXCLUDED

  fun onIgnoredFilesUpdate(ignoredFilePaths: Set<FilePath>, previouslyIgnoredFilePaths: Set<FilePath>) {
    val addedIgnored = ignoredFilePaths.filter { it !in previouslyIgnoredFilePaths }
    if (!addedIgnored.isEmpty()) {
      ignoredUpdateFinished(addedIgnored)
    }
  }

  private fun ignoredUpdateFinished(ignoredPaths: Collection<FilePath>) {
    ProgressManager.checkCanceled()
    if (synchronizationTurnOff()) return
    if (mutedForCurrentProject()) return

    processIgnored(ignoredPaths)
  }

  private fun processIgnored(ignoredPaths: Collection<FilePath>) {
    val ignoredDirs =
      determineIgnoredDirsToExclude(project, ignoredPaths)

    if (allowShowNotification()) {
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
}

private fun markIgnoredAsExcluded(project: Project, files: Collection<VirtualFile>) {
  val ignoredDirsByModule = runReadAction {
    files
      .groupBy { ModuleUtil.findModuleForFile(it, project) }
      //if the directory already excluded then ModuleUtil.findModuleForFile return null and this will filter out such directories from processing.
      .filterKeys(Objects::nonNull)
  }

  for ((module, ignoredDirs) in ignoredDirsByModule) {
    excludeAction.exclude(module!!, ignoredDirs)
  }
}

private fun getProjectSourceRoots(project: Project): Set<VirtualFile> = runReadAction {
  OrderEnumerator.orderEntries(project).withoutSdk().withoutLibraries().sources().usingCache().roots.toHashSet()
}

private fun containsShelfDirectoryOrUnderIt(filePath: FilePath, shelfPath: String) =
  FileUtil.isAncestor(shelfPath, filePath.path, false) ||
  FileUtil.isAncestor(filePath.path, shelfPath, false)

private fun determineIgnoredDirsToExclude(project: Project, ignoredPaths: Collection<FilePath>): List<VirtualFile> {
  val sourceRoots = getProjectSourceRoots(project)
  val fileIndex = ProjectFileIndex.getInstance(project)
  val shelfPath = ShelveChangesManager.getShelfPath(project)

  return ignoredPaths
    .asSequence()
    .filter(FilePath::isDirectory)
    //shelf directory usually contains in project and excluding it prevents local history to work on it
    .filterNot { containsShelfDirectoryOrUnderIt(it, shelfPath) }
    .mapNotNull(FilePath::getVirtualFile)
    .filterNot { runReadAction { fileIndex.isExcluded(it) } }
    //do not propose to exclude if there is a source root inside
    .filterNot { ignored -> sourceRoots.contains(ignored) }
    .toList()
}

private fun selectFilesToExclude(project: Project, ignoredDirs: List<VirtualFile>): Collection<VirtualFile> {
  val dialog = IgnoredToExcludeSelectDirectoriesDialog(project, ignoredDirs)
  if (!dialog.showAndGet()) return emptyList()

  return dialog.selectedFiles
}

private fun allowShowNotification() = Registry.`is`("vcs.propose.add.ignored.directories.to.exclude", true)
private fun synchronizationTurnOff() = !Registry.`is`("vcs.enable.add.ignored.directories.to.exclude", true)

@ApiStatus.Internal
class IgnoredToExcludeNotificationProvider : EditorNotificationProvider, DumbAware {
  private fun canCreateNotification(project: Project, file: VirtualFile): Boolean {
    return file.fileType is IgnoreFileType &&
           with(project.service<IgnoredToExcludedSynchronizer>()) {
             !synchronizationTurnOff() &&
             allowShowNotification() &&
             !mutedForCurrentProject() &&
             isNotEmpty()
           }
  }

  private fun showIgnoredAction(project: Project) {
    val allFiles = project.service<IgnoredToExcludedSynchronizer>().getValidFiles()
    if (allFiles.isEmpty()) return

    val userSelectedFiles = selectFilesToExclude(project, allFiles)
    if (userSelectedFiles.isEmpty()) return

    with(project.service<IgnoredToExcludedSynchronizer>()) {
      markIgnoredAsExcluded(project, userSelectedFiles)
      clearFiles(userSelectedFiles)
    }
  }

  private fun muteAction(project: Project) = Runnable {
    project.service<IgnoredToExcludedSynchronizer>().muteForCurrentProject()
    EditorNotifications.getInstance(project).updateNotifications(this@IgnoredToExcludeNotificationProvider)
  }

  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (!canCreateNotification(project, file)) {
      return null
    }

    return Function { fileEditor ->
      val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info)
      panel.icon(AllIcons.General.BalloonInformation)
      panel.text = message("ignore.to.exclude.notification.message")
      panel.createActionLabel(message("ignore.to.exclude.notification.action.view")) { showIgnoredAction(project) }
      panel.createActionLabel(message("ignore.to.exclude.notification.action.mute"), muteAction(project))
      panel.createActionLabel(message("ignore.to.exclude.notification.action.details")) {
        BrowserUtil.browse("https://www.jetbrains.com/help/idea/content-roots.html#folder-categories")
      }
      panel
    }
  }
}

internal class CheckIgnoredToExcludeAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!

    runModalTask(ActionsBundle.message("action.CheckIgnoredAndNotExcludedDirectories.progress"), project, true) {
      VcsIgnoreManagerImpl.getInstanceImpl(project).awaitRefreshQueue()
      val ignoredFilePaths = ChangeListManager.getInstance(project).ignoredFilePaths
      val dirsToExclude = determineIgnoredDirsToExclude(project, ignoredFilePaths)
      if (dirsToExclude.isEmpty()) {
        VcsNotifier.getInstance(project)
          .notifyMinorInfo(IGNORED_TO_EXCLUDE_NOT_FOUND, "", message("ignore.to.exclude.no.directories.found"))
      }
      else {
        application.invokeAndWait {
          val userSelectedFiles = selectFilesToExclude(project, dirsToExclude)
          if (userSelectedFiles.isNotEmpty()) {
            markIgnoredAsExcluded(project, userSelectedFiles)
          }
        }
      }
    }
  }
}

// do not use SelectFilesDialog.init because it doesn't provide clear statistic: what exactly dialog shown/closed, action clicked
private class IgnoredToExcludeSelectDirectoriesDialog(
  project: Project?,
  files: List<VirtualFile>,
) : SelectFilesDialog(project, files, message("ignore.to.exclude.notification.notice"), null, true, true) {
  init {
    title = message("ignore.to.exclude.view.dialog.title")
    selectedFiles = files
    setOKButtonText(message("ignore.to.exclude.view.dialog.exclude.action"))
    setCancelButtonText(CommonBundle.getCancelButtonText())
    init()
  }
}
