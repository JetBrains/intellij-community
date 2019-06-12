// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ignore

import com.intellij.ProjectTopics
import com.intellij.ide.projectView.actions.MarkExcludeRootAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FilesProcessorWithNotificationImpl
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

private const val ASKED_MARK_IGNORED_FILES_AS_EXCLUDED_PROPERTY = "ASKED_MARK_IGNORED_FILES_AS_EXCLUDED"

private val LOG = logger<IgnoredToExcludedSynchronizer>()

private val excludeAction = object : MarkExcludeRootAction() {
  fun exclude(module: Module, dirs: Collection<VirtualFile>) = runInEdt { modifyRoots(module, dirs.toTypedArray()) }
}

class IgnoredToExcludedSynchronizer(project: Project, parentDisposable: Disposable)
  : VcsIgnoredHolderUpdateListener, FilesProcessorWithNotificationImpl(project, parentDisposable) {

  override val askedBeforeProperty = ASKED_MARK_IGNORED_FILES_AS_EXCLUDED_PROPERTY
  override val doForCurrentProjectProperty: String? = null
  override val showActionText: String = message("ignore.to.exclude.notification.action.view")
  override val forCurrentProjectActionText: String = message("ignore.to.exclude.notification.action.exclude")
  override val forAllProjectsActionText: String? = null
  override val muteActionText: String = message("ignore.to.exclude.notification.action.mute")
  override fun notificationTitle() = ""
  override fun notificationMessage(): String = message("ignore.to.exclude.notification.message")
  override val viewFilesDialogTitle: String? = message("ignore.to.exclude.view.dialog.title")
  override val viewFilesDialogOkActionName: String = message("ignore.to.exclude.view.dialog.exclude.action")

  init {
    project.messageBus.connect(this).subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) = updateNotificationState()
    })
  }

  private fun updateNotificationState() {
    // in case if the project roots changed (e.g. by build tools) then the directories shown in notification can be outdated.
    // filter directories which excluded or containing source roots and expire notification if needed.
    if (notificationNotPresent()) return

    val fileIndex = ProjectFileIndex.getInstance(project)
    val sourceRoots = getProjectSourceRoots()

    val acquiredFiles = acquireValidFiles()
    LOG.debug("updateNotificationState, acquiredFiles", acquiredFiles)
    val filesToRemove = acquiredFiles
      .asSequence()
      .filter { file -> fileIndex.isExcluded(file) || sourceRoots.contains(file) }
      .toList()
    LOG.debug("updateNotificationState, filesToRemove", filesToRemove)
    removeFiles(filesToRemove)

    val files = acquireValidFiles()
    if (files.isEmpty()) {
      expireNotification()
    }
  }

  override fun doActionOnChosenFiles(files: Collection<VirtualFile>) {
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
    if (!isFullRescan) return
    if (!Registry.`is`("vcs.propose.add.ignored.directories.to.exclude", true)) return
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
        .filterNot(fileIndex::isExcluded)
        //do not propose to exclude if there is a source root inside
        .filterNot { ignored -> sourceRoots.contains(ignored) }
        .toList()
    processFiles(ignoredDirs)
  }

  private fun markIgnoredAsExcluded(files: Collection<VirtualFile>) {
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

