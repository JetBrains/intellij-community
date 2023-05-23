// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil
import com.intellij.internal.statistic.libraryJar.findJarVersion
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Callable

internal class LibraryUsageStatisticsProvider(private val project: Project) : DaemonListener {

  init {
    if (!isEnabled) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    if (!isEnabled) return

    val processedFilesService = ProcessedFilesStorageService.getInstance(project)

    for (fileEditor in fileEditors) {
      val vFile = fileEditor.file

      if (processedFilesService.isVisited(vFile)) continue

      StatisticsEventLogProviderUtil.getEventLogProvider("FUS").logger
        .computeAsync { backgroundExecutor ->
          ReadAction.nonBlocking(Callable { processFile(vFile) })
            .finishOnUiThread(ModalityState.any()) {
              if (it != null && processedFilesService.visit(vFile)) {
                LibraryUsageStatisticsStorageService.getInstance(project).increaseUsages(it)
              }
            }
            .inSmartMode(project)
            .expireWith(processedFilesService)
            .coalesceBy(vFile, processedFilesService)
            .submit(backgroundExecutor)
        }
    }
  }

  private fun processFile(vFile: VirtualFile): List<LibraryUsage>? {
    val fileIndex = ProjectFileIndex.getInstance(project)
    if (!fileIndex.isInSource(vFile) || fileIndex.isInLibrary(vFile)) return null

    val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return null

    val importProcessor = LibraryUsageImportProcessorBean.INSTANCE.forLanguage(psiFile.language) ?: return null
    val processedLibraryNames = mutableSetOf<String>()
    val usages = mutableListOf<LibraryUsage>()

    // we should process simple element imports first, because they can be unambiguously resolved
    val imports = importProcessor.imports(psiFile).sortedByDescending { importProcessor.isSingleElementImport(it) }
    for (import in imports) {
      ProgressManager.checkCanceled()

      val qualifier = importProcessor.importQualifier(import) ?: continue
      val libraryName = LibraryUsageDescriptors.findSuitableLibrary(qualifier)?.takeUnless { it in processedLibraryNames } ?: continue

      val libraryElement = importProcessor.resolve(import) ?: continue
      val libraryVersion = findJarVersion(libraryElement) ?: continue

      processedLibraryNames += libraryName
      usages += LibraryUsage(
        name = libraryName,
        version = libraryVersion,
        fileType = psiFile.fileType,
      )
    }

    return usages
  }

  companion object {
    @set:TestOnly
    var isEnforceEnabledInTests: Boolean = false

    val isEnabled: Boolean
      get() {
        return isEnforceEnabledInTests || ApplicationManager.getApplication().run {
          !isUnitTestMode && !isHeadlessEnvironment && StatisticsUploadAssistant.isCollectAllowedOrForced()
        }
      }
  }
}
