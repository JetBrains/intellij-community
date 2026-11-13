// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.project.processIndexedContentFilesByType
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.multiverse.ProjectModelEntityContextProvider
import com.intellij.openapi.application.readAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap

/**
 * Counts the indexed content files of a project by file type and by context count.
 *
 * The files come from [FileTypeIndex], so the collector reads no file and walks no directory. A file that the index
 * does not hold is not counted. Without [FileBasedIndex] the collector reports the `shared_source_support_enabled`
 * event only. `FileTypeUsagesCollector` reads the same files, so the two groups count the same project.
 *
 * The context count of a file comes from one of two rules:
 * - The root rule. A file has the contexts of its nearest root, so only a root with two or more contexts can give a
 *   file two or more contexts. The collector finds those roots in one read action, and it counts every other file as
 *   one context.
 * - The per-file rule. [CodeInsightContextProvider] of Rust and of Radler do not follow the root rule, because the
 *   contexts of a file come from its crates or from its open editor. So the collector asks the provider chain for
 *   each indexed file when a provider besides [ProjectModelEntityContextProvider] is registered.
 */
internal class CodeInsightContextFusCollector : ProjectUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("code.insight.context", 2)

  private val ENABLED = GROUP.registerEvent("shared_source_support_enabled", EventFields.Boolean("enabled"))

  private val CONTEXT_REPORT = GROUP.registerEvent(
    "context_report",
    EventFields.FileType, EventFields.Int("context_number"), EventFields.RoundedInt("file_number")
  )

  override fun getGroup(): EventLogGroup = GROUP

  override suspend fun collect(project: Project): Set<MetricEvent> {
    if (project.isDisposed) return emptySet()

    val sharedSourceEnabled = isSharedSourceSupportEnabled(project)
    val enabledEvent = ENABLED.metric(sharedSourceEnabled)
    if (!sharedSourceEnabled) return setOf(enabledEvent)

    val contextManager = CodeInsightContextManagerImpl.getInstanceImpl(project)
    val multiContextRoots = findMultiContextRoots(project, contextManager)
    val askProviderPerFile = multiContextRoots.isNotEmpty() || hasProviderBesidesProjectModel()

    val fileCounts = HashMap<FileType, Int2IntOpenHashMap>()
    val hasData = processIndexedContentFilesByType(project) { fileType, file ->
      val contextCount =
        if (askProviderPerFile && (multiContextRoots.isEmpty() || VfsUtilCore.isUnder(file, multiContextRoots))) {
          contextManager.getCodeInsightContextsWithoutCaching(file).size
        }
        else {
          1
        }
      fileCounts.getOrPut(fileType) { Int2IntOpenHashMap() }.addTo(contextCount, 1)
    }
    if (!hasData) return setOf(enabledEvent)

    return buildSet {
      add(enabledEvent)
      for ([fileType, fileCountByContextCount] in fileCounts) {
        for (entry in fileCountByContextCount.int2IntEntrySet()) {
          add(CONTEXT_REPORT.metric(fileType, entry.intKey, entry.intValue))
        }
      }
    }
  }

  /**
   * The roots that give two or more contexts to the files under them.
   *
   * A root of one file set has one context at most, so the read asks the provider chain only for a root of two or
   * more file sets. A write action cancels and restarts the read. [WorkspaceFileIndexEx.visitFileSets] does not check
   * the cancellation, so the visitor checks it.
   */
  private suspend fun findMultiContextRoots(project: Project, contextManager: CodeInsightContextManagerImpl): Set<VirtualFile> =
    readAction {
      if (project.isDisposed) return@readAction emptySet()
      val fileSetsPerRoot = HashMap<VirtualFile, Int>()
      WorkspaceFileIndexEx.getInstance(project).visitFileSets { fileSet, _ ->
        ProgressManager.checkCanceled()
        fileSetsPerRoot.merge(fileSet.root, 1, Int::plus)
      }
      fileSetsPerRoot.keys.filterTo(HashSet()) { root ->
        ProgressManager.checkCanceled()
        fileSetsPerRoot.getValue(root) >= 2 && contextManager.getCodeInsightContextsWithoutCaching(root).size >= 2
      }
    }

  private fun hasProviderBesidesProjectModel(): Boolean =
    CONTEXT_PROVIDER_EP.extensionList.any { it !is ProjectModelEntityContextProvider }
}

private val CONTEXT_PROVIDER_EP: ExtensionPointName<CodeInsightContextProvider> =
  ExtensionPointName.create("com.intellij.multiverse.codeInsightContextProvider")
