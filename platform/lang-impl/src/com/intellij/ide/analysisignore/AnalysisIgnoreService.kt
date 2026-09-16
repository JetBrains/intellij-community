// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class AnalysisIgnoreService(private val project: Project, coroutineScope: CoroutineScope) {

  private val filesToRead = ConcurrentHashMap.newKeySet<VirtualFile>()
  private val baseDirsToForget = ConcurrentHashMap.newKeySet<String>()
  private val subtreesToForget = ConcurrentHashMap.newKeySet<String>()

  private val requests = Channel<Unit>(capacity = Channel.CONFLATED)

  init {
    coroutineScope.launch(Dispatchers.Default) {
      runLoop()
    }
    // If the feature is disabled we will clean up workspace entities
    requests.trySend(Unit)
  }

  /**
   * Adds every change of one batch to the queue.
   */
  fun scheduleChanges(
    files: Collection<VirtualFile> = emptyList(),
    baseDirUrls: Collection<String> = emptyList(),
    subtreeUrls: Collection<String> = emptyList(),
  ) {
    if (files.isEmpty() && baseDirUrls.isEmpty() && subtreeUrls.isEmpty()) return

    filesToRead.addAll(files)
    baseDirsToForget.addAll(baseDirUrls)
    subtreesToForget.addAll(subtreeUrls)
    requests.trySend(Unit)
  }

  /**
   * Returns the URLs of the directories that hold a `.analysisignore` file according to the Workspace Model.
   */
  fun knownBaseDirUrls(): Set<String> =
    WorkspaceModel.getInstance(project).currentSnapshot.entities<AnalysisIgnoreEntity>().mapTo(HashSet()) { it.baseDir.url }

  /**
   * Stores the patterns of [file] at once if it is a `.analysisignore` file.
   */
  @RequiresReadLockAbsence(generateAssertion = false)
  fun applyNow(file: VirtualFile) {
    ThreadingAssertions.assertNoReadAccess()
    filesToRead.remove(file)
    val model = WorkspaceModel.getInstance(project)
    val record = readIfRelevant(file, model) ?: return
    baseDirsToForget.remove(record.baseDir.url)
    if (record.changes(model.currentSnapshot)) {
      writeNow(model, records = listOf(record), forgottenBaseDirUrls = emptyList())
    }
  }

  /**
   * Removes the entity of the directory at [baseDirUrl] at once.
   */
  @RequiresReadLockAbsence(generateAssertion = false)
  fun forgetNow(baseDirUrl: String) {
    ThreadingAssertions.assertNoReadAccess()
    val model = WorkspaceModel.getInstance(project)
    if (model.currentSnapshot.findAnalysisIgnoreEntity(baseDirUrl) == null) return
    writeNow(model, records = emptyList(), forgottenBaseDirUrls = listOf(baseDirUrl))
  }

  /**
   * Applies every queued change in one update of the Workspace Model.
   */
  suspend fun processNow() {
    val model = project.serviceAsync<WorkspaceModel>()
    if (!Registry.`is`(ANALYSIS_IGNORE_ENABLED_KEY, true)) {
      removeEverything(model)
      return
    }

    val files = drain(filesToRead)
    val baseDirUrls = drain(baseDirsToForget).toHashSet()
    val subtreeUrls = drain(subtreesToForget)

    val records = ArrayList<AnalysisIgnoreRecord>()
    withContext(Dispatchers.IO) {
      for (file in files) {
        val record = readIfRelevant(file, model) ?: continue
        baseDirUrls.remove(record.baseDir.url)
        if (record.changes(model.currentSnapshot)) {
          records.add(record)
        }
      }
    }
    val forgottenBaseDirUrls = model.currentSnapshot.entities<AnalysisIgnoreEntity>()
      .map { it.baseDir.url }
      .filter { url -> url in baseDirUrls || subtreeUrls.any { isUnderOrEqual(url, it) } }
      .toList()
    if (records.isEmpty() && forgottenBaseDirUrls.isEmpty()) return

    model.update(UPDATE_DESCRIPTION, updater(records, forgottenBaseDirUrls))
  }

  private fun readIfRelevant(file: VirtualFile, model: WorkspaceModel): AnalysisIgnoreRecord? {
    if (!file.isValid || !file.isAnalysisIgnoreFile()) return null
    val baseDirUrl = file.parent?.url ?: return null
    if (model.currentSnapshot.findAnalysisIgnoreEntity(baseDirUrl) == null && !isInIndexableContent(file)) return null

    return readAnalysisIgnoreRecord(file, model.getVirtualFileUrlManager())
  }

  private fun AnalysisIgnoreRecord.changes(snapshot: EntityStorage): Boolean =
    patterns != snapshot.findAnalysisIgnoreEntity(baseDir.url)?.patterns.orEmpty()

  private fun writeNow(model: WorkspaceModel, records: List<AnalysisIgnoreRecord>, forgottenBaseDirUrls: List<String>) {
    val updater = updater(records, forgottenBaseDirUrls)
    runBlockingMaybeCancellable { model.update(UPDATE_DESCRIPTION, updater) }
  }

  private fun updater(records: List<AnalysisIgnoreRecord>, forgottenBaseDirUrls: List<String>): (MutableEntityStorage) -> Unit = { builder ->
    for (baseDirUrl in forgottenBaseDirUrls) {
      builder.findAnalysisIgnoreEntity(baseDirUrl)?.let { builder.removeEntity(it) }
    }
    for (record in records) {
      val entity = builder.findAnalysisIgnoreEntity(record.baseDir.url)
      when {
        record.patterns.isEmpty() -> entity?.let { builder.removeEntity(it) }
        entity == null -> {
          val patterns = StringUtil.pluralize("pattern", record.patterns.size)
          LOG.info("Found $ANALYSIS_IGNORE_FILE_NAME in ${record.baseDir.presentableUrl} with ${record.patterns.size} $patterns")
          builder.addEntity(AnalysisIgnoreEntity(record.baseDir, record.patterns, AnalysisIgnoreEntitySource))
        }
        entity.patterns != record.patterns -> builder.modifyAnalysisIgnoreEntity(entity) {
          patterns = record.patterns.toMutableList()
        }
      }
    }
  }

  private suspend fun removeEverything(model: WorkspaceModel) {
    filesToRead.clear()
    baseDirsToForget.clear()
    subtreesToForget.clear()
    if (model.currentSnapshot.entities<AnalysisIgnoreEntity>().none()) return

    model.update("Remove exclusions declared in $ANALYSIS_IGNORE_FILE_NAME files") { builder ->
      for (entity in builder.entities<AnalysisIgnoreEntity>()) {
        builder.removeEntity(entity)
      }
    }
  }

  private fun isInIndexableContent(file: VirtualFile): Boolean {
    val info = (WorkspaceFileIndex.getInstance(project) as WorkspaceFileIndexEx).getFileInfo(
      file,
      honorExclusion = false,
      includeContentSets = true,
      includeContentNonIndexableSets = false,
      includeExternalSets = false,
      includeExternalSourceSets = false,
      includeExternalNonIndexableSets = false,
      includeCustomKindSets = false,
    )
    return info !is WorkspaceFileInternalInfo.NonWorkspace
  }

  private fun <T> drain(set: MutableSet<T>): List<T> {
    val drained = ArrayList<T>(set.size)
    for (element in set) {
      if (set.remove(element)) {
        drained.add(element)
      }
    }
    return drained
  }

  private suspend fun runLoop() {
    while (true) {
      requests.receive()
      // `withTimeoutOrNull` returns `null` when the quiet period passes without a request. Each request starts the wait again.
      while (withTimeoutOrNull(QUIET_PERIOD) { requests.receive() } != null) {
        // ignore
      }
      try {
        processNow()
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        LOG.error("Failed to update the exclusions declared in $ANALYSIS_IGNORE_FILE_NAME files", e)
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): AnalysisIgnoreService = project.service()

    private val QUIET_PERIOD = 400.milliseconds

    private const val UPDATE_DESCRIPTION = "Update exclusions declared in $ANALYSIS_IGNORE_FILE_NAME files"

    private val LOG = logger<AnalysisIgnoreService>()
  }
}

/**
 * Returns the entity of the `.analysisignore` file in the directory at [baseDirUrl], or `null` if that directory has none.
 */
private fun EntityStorage.findAnalysisIgnoreEntity(baseDirUrl: String): AnalysisIgnoreEntity? =
  entities<AnalysisIgnoreEntity>().firstOrNull { it.baseDir.url == baseDirUrl }
