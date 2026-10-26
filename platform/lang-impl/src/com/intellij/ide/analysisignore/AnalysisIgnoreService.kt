// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
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
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo
import com.intellij.workspaceModel.ide.ProjectRootEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class AnalysisIgnoreService(private val project: Project, private val coroutineScope: CoroutineScope) {

  private val filesToRead = ConcurrentHashMap.newKeySet<VirtualFile>()
  private val baseDirsToForget = ConcurrentHashMap.newKeySet<String>()
  private val subtreesToForget = ConcurrentHashMap.newKeySet<String>()

  // A new file while its fill with the default lines runs. The queue does not read such a file, see [fill].
  private val fillJobs = ConcurrentHashMap<VirtualFile, Job>()

  private val requests = Channel<Unit>(capacity = Channel.CONFLATED)

  // A change of the default lines in the settings. The default entities then get the new lines, see [syncDefaultsOnChanges].
  private val defaultLinesChanges = Channel<Unit>(capacity = Channel.CONFLATED)

  init {
    ApplicationManager.getApplication().messageBus.connect(coroutineScope)
      .subscribe(AnalysisIgnoreDefaultsListener.TOPIC, AnalysisIgnoreDefaultsListener { defaultLinesChanges.trySend(Unit) })
    coroutineScope.launch(Dispatchers.Default) {
      runLoop()
    }
    coroutineScope.launch(Dispatchers.Default) {
      syncDefaultsOnChanges()
    }
    // If the feature is disabled we will clean up workspace entities
    requests.trySend(Unit)
  }

  /**
   * Adds every change of one batch to the queue. A file of [createdFiles] that is still empty gets the
   * [default lines][AnalysisIgnoreDefaults.linesOfNewFileIn] of its directory. So a new file in a project root directory keeps the defaults
   * in effect, and a balloon tells the user.
   */
  fun scheduleChanges(
    files: Collection<VirtualFile> = emptyList(),
    baseDirUrls: Collection<String> = emptyList(),
    subtreeUrls: Collection<String> = emptyList(),
    createdFiles: Collection<VirtualFile> = emptyList(),
  ) {
    if (files.isEmpty() && baseDirUrls.isEmpty() && subtreeUrls.isEmpty() && createdFiles.isEmpty()) return

    filesToRead.addAll(files)
    baseDirsToForget.addAll(baseDirUrls)
    subtreesToForget.addAll(subtreeUrls)
    if (AnalysisIgnoreDefaults.areEnabled()) {
      for (file in createdFiles) {
        val job = coroutineScope.launch(Dispatchers.Default, start = CoroutineStart.LAZY) { fill(file) }
        if (fillJobs.putIfAbsent(file, job) == null) job.start() else job.cancel()
      }
    }
    requests.trySend(Unit)
  }

  /** Waits until every file of [scheduleChanges] got its default lines or was queued for a read. */
  @TestOnly
  suspend fun awaitPendingFills() {
    fillJobs.values.toList().joinAll()
  }

  /**
   * Returns the URLs of the directories that hold a `.analysisignore` file according to the Workspace Model. A default entity has no file,
   * and thus its directory is not in the result.
   */
  fun knownBaseDirUrls(): Set<String> =
    WorkspaceModel.getInstance(project).currentSnapshot.fileEntities().mapTo(HashSet()) { it.baseDir.url }

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

  fun applyInWriteAction(file: VirtualFile) {
    ThreadingAssertions.assertWriteAccess()
    filesToRead.remove(file)
    val model = WorkspaceModel.getInstance(project)
    val record = readIfRelevant(file, model) ?: return
    baseDirsToForget.remove(record.baseDir.url)
    if (record.changes(model.currentSnapshot)) {
      model.updateProjectModel(UPDATE_DESCRIPTION, updater(records = listOf(record), forgottenBaseDirUrls = emptyList()))
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
   * Gives each project root its [default entity][AnalysisIgnoreDefaultEntitySource] at once, if the Workspace Model is not in sync. The
   * scan calls this before it starts, so that it never sees a project root without its defaults.
   */
  @RequiresReadLockAbsence(generateAssertion = false)
  fun syncDefaultsBlocking() {
    ThreadingAssertions.assertNoReadAccess()
    if (!Registry.`is`(ANALYSIS_IGNORE_ENABLED_KEY, true)) return
    val model = WorkspaceModel.getInstance(project)
    if (model.currentSnapshot.defaultsInSync()) return
    runBlockingMaybeCancellable { model.update(SYNC_DEFAULTS_DESCRIPTION) { it.syncDefaults() } }
  }

  /**
   * Applies every queued change in one update of the Workspace Model. The same update keeps the
   * [default entities][AnalysisIgnoreDefaultEntitySource] in sync with the project roots.
   */
  suspend fun processNow() {
    val model = project.serviceAsync<WorkspaceModel>()
    if (!Registry.`is`(ANALYSIS_IGNORE_ENABLED_KEY, true)) {
      removeEverything(model)
      return
    }

    // A file that waits for its fill stays unread: an empty new file would otherwise remove the defaults of its project root, and its lines
    // would then add them back. The fill applies the file itself, or queues it again.
    val files = drain(filesToRead).filterNot { fillJobs.containsKey(it) }
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
    val forgottenBaseDirUrls = model.currentSnapshot.fileEntities()
      .map { it.baseDir.url }
      .filter { url -> url in baseDirUrls || subtreeUrls.any { isUnderOrEqual(url, it) } }
      .toList()
    if (records.isEmpty() && forgottenBaseDirUrls.isEmpty() && model.currentSnapshot.defaultsInSync()) return

    model.update(UPDATE_DESCRIPTION, updater(records, forgottenBaseDirUrls))
  }

  /**
   * Writes the [default lines][AnalysisIgnoreDefaults.linesOfNewFileIn] into [file] if it is still empty. The fill runs apart from
   * [processNow], so that a modal dialog delays the fill alone and not the queue. The writer applies a filled file at once. A file that
   * gets no lines, as one that the user typed into first, goes back to the queue.
   */
  private suspend fun fill(file: VirtualFile) {
    var filled = false
    try {
      filled = fillIfEmpty(file)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error("Failed to add the default lines to ${file.presentableUrl}", e)
    }
    finally {
      fillJobs.remove(file)
      if (!filled) {
        filesToRead.add(file)
        requests.trySend(Unit)
      }
    }
  }

  private suspend fun fillIfEmpty(file: VirtualFile): Boolean {
    if (!file.isValid || !file.isAnalysisIgnoreFile() || file.length > 0) return false
    val baseDir = file.parent ?: return false
    val lines = readAction {
      if (isInIndexableContent(file)) AnalysisIgnoreDefaults.linesOfNewFileIn(project, baseDir) else emptyList()
    }
    if (lines.isEmpty()) return false
    val filled = withContext(Dispatchers.EDT) { AnalysisIgnoreFileWriter.fillEmptyFile(project, file, lines) }
    if (filled) {
      notifyDefaultLinesCopied(project)
    }
    return filled
  }

  private fun readIfRelevant(file: VirtualFile, model: WorkspaceModel): AnalysisIgnoreRecord? {
    if (!file.isValid || !file.isAnalysisIgnoreFile()) return null
    val baseDirUrl = file.parent?.url ?: return null
    if (model.currentSnapshot.findAnalysisIgnoreEntity(baseDirUrl) == null && !isInIndexableContent(file)) return null

    return readAnalysisIgnoreRecord(file, model.getVirtualFileUrlManager())
  }

  /**
   * Returns `true` if the record changes the entity of its directory. A file without patterns still gets an entity, because it removes the
   * default entity of its project root.
   */
  private fun AnalysisIgnoreRecord.changes(snapshot: EntityStorage): Boolean {
    val entity = snapshot.findAnalysisIgnoreEntity(baseDir.url) ?: return true
    return patterns != entity.patterns
  }

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
    // The update that adds the first file of a project root also removes the default entity of that root.
    builder.syncDefaults()
  }

  /**
   * Syncs the [default entities][AnalysisIgnoreDefaultEntitySource] once at the start, and then at once when the project roots or the
   * default lines change. Both skip the quiet period, so that a root gets its default entity before the scan of the root starts. The update
   * reads no file, and thus it needs no order with [processNow].
   */
  private suspend fun syncDefaultsOnChanges() {
    val model = project.serviceAsync<WorkspaceModel>()
    updateDefaults(model)
    merge(
      model.eventLog.filter { it.getChanges(ProjectRootEntity::class.java).isNotEmpty() }.map { },
      defaultLinesChanges.receiveAsFlow(),
    ).collect { updateDefaults(model) }
  }

  private suspend fun updateDefaults(model: WorkspaceModel) {
    if (!Registry.`is`(ANALYSIS_IGNORE_ENABLED_KEY, true) || model.currentSnapshot.defaultsInSync()) return
    try {
      model.update(SYNC_DEFAULTS_DESCRIPTION) { it.syncDefaults() }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error("Failed to update the default exclusions of the project roots", e)
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

    private const val SYNC_DEFAULTS_DESCRIPTION = "Update the default exclusions of the project roots"

    private val LOG = logger<AnalysisIgnoreService>()
  }
}

/**
 * Returns the entity of the `.analysisignore` file in the directory at [baseDirUrl], or `null` if that directory has none. The
 * [default entity][AnalysisIgnoreDefaultEntitySource] of a project root is not a file, and thus it is never the result.
 */
private fun EntityStorage.findAnalysisIgnoreEntity(baseDirUrl: String): AnalysisIgnoreEntity? =
  fileEntities().firstOrNull { it.baseDir.url == baseDirUrl }

/**
 * Returns the project roots that get a [default entity][AnalysisIgnoreDefaultEntitySource], by their URLs. These are the roots without a
 * `.analysisignore` file in the root directory. An empty file counts as a file. A file below the root does not count: its lines add to the
 * defaults. The result is empty while the [defaults][AnalysisIgnoreDefaults.areEnabled] are off.
 */
private fun EntityStorage.rootsWithDefaults(): Map<String, VirtualFileUrl> {
  if (!AnalysisIgnoreDefaults.areEnabled()) return emptyMap()
  val fileBaseDirUrls = fileEntities().mapTo(HashSet()) { it.baseDir.url }
  return entities<ProjectRootEntity>()
    .filter { it.root.url !in fileBaseDirUrls }
    .associateBy({ it.root.url }, { it.root })
}

/**
 * Returns `true` if each root of [rootsWithDefaults] holds one [default entity][AnalysisIgnoreDefaultEntitySource] with the
 * [default lines][AnalysisIgnoreDefaults.lines], and no other default entity exists.
 */
private fun EntityStorage.defaultsInSync(): Boolean {
  val rootUrls = rootsWithDefaults().keys
  val defaults = defaultEntities()
  val lines = AnalysisIgnoreDefaults.lines
  return defaults.size == rootUrls.size &&
         defaults.mapTo(HashSet()) { it.baseDir.url } == rootUrls &&
         defaults.all { it.patterns == lines }
}

/**
 * Gives each root of [rootsWithDefaults] a [default entity][AnalysisIgnoreDefaultEntitySource], and removes every other default entity.
 * Thus, the `.analysisignore` file in the root directory removes its default entity, and the removal of that file brings it back.
 * Updates the patterns of a default entity from an older product version, or after the user changed the default lines.
 */
private fun MutableEntityStorage.syncDefaults() {
  val lines = AnalysisIgnoreDefaults.lines
  val roots = rootsWithDefaults()
  val withDefaults = HashSet<String>()
  for (entity in defaultEntities()) {
    when {
      entity.baseDir.url !in roots || !withDefaults.add(entity.baseDir.url) -> removeEntity(entity)
      entity.patterns != lines -> modifyAnalysisIgnoreEntity(entity) {
        patterns = lines.toMutableList()
      }
    }
  }
  for ((url, root) in roots) {
    if (url !in withDefaults) {
      addEntity(AnalysisIgnoreEntity(root, lines, AnalysisIgnoreDefaultEntitySource))
    }
  }
}
