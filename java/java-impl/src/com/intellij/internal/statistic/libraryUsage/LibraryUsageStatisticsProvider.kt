// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.internal.statistic.libraryJar.findJarVersion
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.TestOnly

internal class LibraryUsageStatisticsProvider(
  private val project: Project,
  private val storageService: LibraryUsageStatisticsStorageService,
  private val libraryDescriptorFinder: LibraryDescriptorFinder,
) : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    if (!isEnabled) return

    ReadAction.nonBlocking { processFile(file) }
      .inSmartMode(source.project)
      .expireWith(storageService)
      .coalesceBy(file, storageService)
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  private fun processFile(vFile: VirtualFile) {
    if (storageService.isVisited(vFile)) return
    val fileIndex = ProjectFileIndex.getInstance(project)
    if (!fileIndex.isInSource(vFile) || fileIndex.isInLibrary(vFile)) return

    val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return

    val fileType = psiFile.fileType
    val importProcessor = LibraryUsageImportProcessor.EP_NAME.findFirstSafe { it.isApplicable(fileType) } ?: return
    val libraryNames = mutableSetOf<String>()
    val usages = mutableListOf<LibraryUsage>()

    // we should process simple element imports first, because they can be unambiguously resolved
    val imports = importProcessor.imports(psiFile).sortedByDescending { importProcessor.isSingleElementImport(it) }
    for (import in imports) {
      ProgressManager.checkCanceled()

      val qualifier = importProcessor.importQualifier(import) ?: continue
      val libraryName = libraryDescriptorFinder.findSuitableLibrary(qualifier)?.takeUnless { it in libraryNames } ?: continue

      val libraryElement = importProcessor.resolve(import) ?: continue
      val libraryVersion = findJarVersion(libraryElement) ?: continue

      libraryNames += libraryName
      usages += LibraryUsage(
        name = libraryName,
        version = libraryVersion,
        fileType = fileType,
      )
    }

    storageService.increaseUsages(vFile, usages)
  }

  companion object {
    @set:TestOnly
    var isEnforceEnabledInTests: Boolean = false

    val isEnabled: Boolean
      get() {
        return isEnforceEnabledInTests || ApplicationManager.getApplication().run {
          !isUnitTestMode && !isHeadlessEnvironment && StatisticsUploadAssistant.isSendAllowed()
        }
      }
  }
}