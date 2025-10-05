// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.internal.statistic.libraryJar.findCorrespondingVirtualFile
import com.intellij.internal.statistic.libraryJar.findJarVersion
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.*

internal class LibraryUsageStatisticsProvider(private val project: Project) : DaemonListener {
  init {
    if (!isEnabled) {
      throw ExtensionNotApplicableException.create()
    }
  }

  private val isEnabled: Boolean
    get() {
      return ApplicationManager.getApplication().run {
        !isUnitTestMode && !isHeadlessEnvironment && StatisticsUploadAssistant.isCollectAllowedOrForced()
      }
    }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    if (!isEnabled) return

    val processedFilesService = ProcessedFilesStorageService.getInstance(project)
    val reporter = LibraryUsageStatisticReporter(project)

    for (fileEditor in fileEditors) {
      val vFile = fileEditor.file

      if (processedFilesService.isVisited(vFile)) continue

      reporter.addStatsFromFile(vFile)
    }
  }
}

public class LibraryUsageStatisticReporter(private val project: Project) {
  public fun addStatsFromFile(vFile: VirtualFile) {
    project.service<FileImportsCollector>().run(vFile)
  }
}

@Service(Service.Level.PROJECT)
private class FileImportsCollector(val project: Project,
                                   val coroutineScope: CoroutineScope) {

  @OptIn(ExperimentalCoroutinesApi::class)
  private val limitedDispatcher = Dispatchers.Default.limitedParallelism(1)

  fun run(vFile: VirtualFile) {
    coroutineScope.launch {
      val usages = findUsages(vFile)

      if (ProcessedFilesStorageService.getInstance(project).visit(vFile)) {
        LibraryUsageStatisticsStorageService.getInstance(project).increaseUsages(usages)
      }
    }
  }

  private suspend fun findUsages(vFile: VirtualFile): List<LibraryUsage> {
    val usages = mutableListOf<LibraryUsage>()

    val libraryFileData = smartReadAction(project) {
      if (!vFile.isValid) return@smartReadAction null

      val fileIndex = ProjectFileIndex.getInstance(project)
      if (!fileIndex.isInSource(vFile) || fileIndex.isInLibrary(vFile)) return@smartReadAction null

      val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return@smartReadAction null
      val fileType = psiFile.fileType

      val importProcessor = LibraryUsageImportProcessorBean.INSTANCE.forLanguage(psiFile.language) ?: return@smartReadAction null
      val processedLibraryNames = mutableSetOf<String>()

      // we should process simple element imports first, because they can be unambiguously resolved
      val imports = importProcessor.imports(psiFile).sortedByDescending { importProcessor.isSingleElementImport(it) }
      val data = imports.mapNotNull { import ->
        val qualifier = importProcessor.importQualifier(import) ?: return@mapNotNull null
        val libraryName = LibraryUsageDescriptors.findSuitableLibrary(qualifier)
                            ?.takeUnless { it in processedLibraryNames }
                          ?: return@mapNotNull null

        val libraryElement = importProcessor.resolve(import) ?: return@mapNotNull null
        val libraryFile = libraryElement.findCorrespondingVirtualFile() ?: return@mapNotNull null

        processedLibraryNames += libraryName

        libraryName to libraryFile
      }

      FileImports(fileType, data.toMap())
    }

    if (libraryFileData == null) return emptyList()

    withContext(limitedDispatcher) {
      val fileType = libraryFileData.fileType
      for ((libraryName, libraryFile) in libraryFileData.data) {
        val libraryVersion = findJarVersion(libraryFile) ?: continue

        usages += LibraryUsage(
          name = libraryName,
          version = libraryVersion,
          fileType = fileType,
        )
      }
    }

    return usages
  }

  private data class FileImports(val fileType: FileType,
                                 val data: Map<String, VirtualFile>)
}