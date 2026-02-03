// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core

import com.intellij.history.ActivityId
import com.intellij.history.ByteContent
import com.intellij.history.core.changes.*
import com.intellij.history.core.tree.Entry
import com.intellij.history.core.tree.RootEntry
import com.intellij.history.integration.IdeaGateway
import com.intellij.history.utils.LocalHistoryLog
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

/**
 * Facade for managing local history operations.
 *
 * Provides methods for managing [com.intellij.history.core.changes.Change].
 * E.g., modifying content, renaming, changing read-only status, moving, and deleting.
 * It also allows adding and managing system and user labels and aggregate changes by [ActivityId]
 */
open class LocalHistoryFacade internal constructor() {

  internal val storageDir: Path
    get() = Path.of(PathManager.getSystemPath(), "LocalHistory")

  internal val changeList: ChangeList

  init {
    changeList = ChangeList(createStorage())
  }

  @ApiStatus.Internal
  protected open fun createStorage(): ChangeListStorage {
    var storage: ChangeListStorage
    try {
      storage = ChangeListStorageImpl(storageDir)
    }
    catch (e: Throwable) {
      LocalHistoryLog.LOG.warn("cannot create storage, in-memory  implementation will be used", e)
      storage = InMemoryChangeListStorage()
    }

    return storage
  }

  private val listeners: MutableList<Listener> = ContainerUtil.createLockFreeCopyOnWriteList()

  @get:ApiStatus.Internal
  @get:TestOnly
  val changeListInTests: ChangeList get() = changeList
  internal val changes: Iterable<ChangeSet> get() = changeList.iterChanges()

  fun beginChangeSet() {
    changeList.beginChangeSet()
  }

  fun forceBeginChangeSet() {
    val lastChangeSet = changeList.forceBeginChangeSet()
    if (lastChangeSet != null) {
      fireChangeSetFinished(lastChangeSet)
    }
  }

  @JvmOverloads
  fun endChangeSet(name: @NlsContexts.Label String?, activityId: ActivityId? = null) {
    val lastChangeSet = changeList.endChangeSet(name, activityId)
    if (lastChangeSet != null) {
      fireChangeSetFinished(lastChangeSet)
    }
  }

  fun created(path: String, isDirectory: Boolean) {
    addChange(if (isDirectory) CreateDirectoryChange(changeList.nextId(), path)
              else CreateFileChange(changeList.nextId(), path))
  }

  @ApiStatus.Internal
  fun contentChanged(path: String, oldContent: Content, oldTimestamp: Long) {
    addChange(ContentChange(changeList.nextId(), path, oldContent, oldTimestamp))
  }

  fun renamed(path: String, oldName: String) {
    addChange(RenameChange(changeList.nextId(), path, oldName))
  }

  fun readOnlyStatusChanged(path: String, oldStatus: Boolean) {
    addChange(ROStatusChange(changeList.nextId(), path, oldStatus))
  }

  fun moved(path: String, oldParent: String) {
    addChange(MoveChange(changeList.nextId(), path, oldParent))
  }

  fun deleted(path: String, deletedEntry: Entry) {
    addChange(DeleteChange(changeList.nextId(), path, deletedEntry))
  }

  @ApiStatus.Internal
  fun putSystemLabel(name: @NlsContexts.Label String, projectId: String, color: Int): LabelImpl {
    return putLabel(PutSystemLabelChange(changeList.nextId(), name, projectId, color))
  }

  @ApiStatus.Internal
  fun putUserLabel(name: @NlsContexts.Label String, projectId: String): LabelImpl {
    return putLabel(PutLabelChange(changeList.nextId(), name, projectId))
  }

  private fun addChange(c: Change) {
    beginChangeSet()
    changeList.addChange(c)
    fireChangeAdded(c)
    endChangeSet(null)
  }

  @TestOnly
  fun addChangeInTests(c: StructuralChange) {
    addChange(c)
  }

  private fun putLabel(c: PutLabelChange): LabelImpl {
    addChange(c)
    return object : LabelImpl {
      override fun getLabelChangeId() = c.id
      override fun getByteContent(root: RootEntry, path: String) = getByteContentBefore(root, path, c)
    }
  }

  @TestOnly
  fun putLabelInTests(c: PutLabelChange) {
    putLabel(c)
  }

  private fun getByteContentBefore(root: RootEntry, path: String, change: Change): ByteContent {
    val rootCopy = root.copy()
    val newPath = revertUpToChange(rootCopy, change.id, path, false, false)
    val entry = rootCopy.findEntry(newPath)
    if (entry == null) return ByteContent(false, null)
    if (entry.isDirectory) return ByteContent(true, null)

    return ByteContent(false, entry.content.bytesIfAvailable)
  }

  fun accept(v: ChangeVisitor): Unit = changeList.accept(v)

  @ApiStatus.Internal
  fun revertUpToChangeSet(root: RootEntry, changeSetId: Long, path: String, revertTarget: Boolean, warnOnFileNotFound: Boolean): String {
    var entryPath = path
    for (changeSet in changes) {
      if (!revertTarget && changeSet.id == changeSetId) break
      for (change in changeSet.changes.reversed()) {
        if (change is StructuralChange && change.affectsPath(entryPath)) {
          change.revertOn(root, warnOnFileNotFound)
          entryPath = change.revertPath(entryPath)
        }
      }
      if (revertTarget && changeSet.id == changeSetId) break
    }
    return entryPath
  }

  @ApiStatus.Internal
  fun revertUpToChange(root: RootEntry, changeId: Long, path: String, revertTarget: Boolean, warnOnFileNotFound: Boolean): String {
    var entryPath = path
    changeSetLoop@ for (changeSet in changes) {
      for (change in changeSet.changes.reversed()) {
        if (!revertTarget && change.id == changeId) break@changeSetLoop
        if (change is StructuralChange && change.affectsPath(entryPath)) {
          change.revertOn(root, warnOnFileNotFound)
          entryPath = change.revertPath(entryPath)
        }
        if (revertTarget && change.id == changeId) break@changeSetLoop
      }
    }
    return entryPath
  }

  fun addListener(l: Listener, parent: Disposable?) {
    listeners.add(l)

    if (parent != null) {
      Disposer.register(parent) { listeners.remove(l) }
    }
  }

  fun removeListener(l: Listener) {
    listeners.remove(l)
  }

  private fun fireChangeAdded(c: Change) {
    for (each in listeners) {
      each.changeAdded(c)
    }
  }

  private fun fireChangeSetFinished(changeSet: ChangeSet) {
    for (each in listeners) {
      each.changeSetFinished(changeSet)
    }
  }

  abstract class Listener {
    open fun changeAdded(c: Change): Unit = Unit
    open fun changeSetFinished(changeSet: ChangeSet): Unit = Unit
  }
}

@ApiStatus.Internal
interface ChangeProcessor {
  fun process(changeSet: ChangeSet, change: Change, changePath: String)
}

@ApiStatus.Internal
open class ChangeProcessorBase(
  private val projectId: String?,
  private val filter: HistoryPathFilter?,
  private val consumer: (ChangeSet) -> Unit,
) : ChangeProcessor {
  private val processedChangesSets = mutableSetOf<Long>()

  override fun process(changeSet: ChangeSet, change: Change, changePath: String) {
    if (!processedChangesSets.contains(changeSet.id) && change.matches(projectId, changePath, filter)) {
      processedChangesSets.add(changeSet.id)
      consumer(changeSet)
    }
  }
}

@ApiStatus.Internal
class ChangeAndPathProcessor(
  projectId: String?,
  filter: HistoryPathFilter?,
  private val pathConsumer: (String) -> Unit,
  changeConsumer: (ChangeSet) -> Unit,
) : ChangeProcessorBase(projectId, filter, changeConsumer) {
  override fun process(changeSet: ChangeSet, change: Change, changePath: String) {
    pathConsumer(changePath)
    super.process(changeSet, change, changePath)
  }
}

@ApiStatus.Experimental
fun LocalHistoryFacade.collectChanges(projectId: String?, startPath: String, filter: HistoryPathFilter?, consumer: (ChangeSet) -> Unit) {
  collectChanges(startPath, ChangeProcessorBase(projectId, filter, consumer))
}

@ApiStatus.Internal
fun LocalHistoryFacade.collectChanges(startPath: String, processor: ChangeProcessor) {
  var path = startPath
  var pathExists = true
  for (changeSet in changes) {
    val changeSetChanges = changeSet.changes
    val singleLabel = changeSetChanges.singleOrNull() as? PutLabelChange
    if (singleLabel != null) {
      if (pathExists) processor.process(changeSet, singleLabel, path)
    }
    else {
      for (change in changeSetChanges.reversed()) {
        if (change is PutLabelChange) continue
        if (!pathExists) {
          if (change is StructuralChange) path = change.revertPath(path)
          if (change is DeleteChange && change.isDeletionOf(path)) {
            processor.process(changeSet, change, path)
            pathExists = true
          }
        }
        else {
          processor.process(changeSet, change, path)
          if (change is StructuralChange) path = change.revertPath(path)
          if (change is CreateEntryChange && change.isCreationalFor(path)) pathExists = false
        }
      }
    }
  }
}

@ApiStatus.Experimental
class HistoryPathFilter private constructor(private val guessedProjectDir: String?, pathFilter: String) {
  val matcher: MinusculeMatcher = NameUtil.buildMatcher("*$pathFilter").build()

  /**
   * If project dir is guessed, then matches part of [path] relative to it, otherwise only last [path] segment is matched
   */
  fun affectsMatching(path: String): Boolean {
    val partToMatch =
      if (guessedProjectDir != null && path.startsWith(guessedProjectDir)) path.substring(guessedProjectDir.length)
      else Paths.getNameOf(path)
    return matcher.matches(partToMatch)
  }

  companion object {
    @JvmStatic
    fun create(pathPattern: String?, project: Project): HistoryPathFilter? =
      if (pathPattern == null) null else HistoryPathFilter(project.guessProjectDir()?.path, pathPattern)

    @TestOnly
    @JvmStatic
    @JvmName("create")
    internal fun create(guessedProjectDir: String?, pathPattern: String?): HistoryPathFilter? =
      if (pathPattern == null) null else HistoryPathFilter(guessedProjectDir, pathPattern)
  }
}

internal fun Change.matches(projectId: String?, path: String, pathFilter: HistoryPathFilter?): Boolean =
  if (affectsPath(path) || affectsProject(projectId)) {
    pathFilter?.let { affectsMatching(it) } ?: true
  } else false

@ApiStatus.Internal
fun LocalHistoryFacade.processContents(
  gateway: IdeaGateway,
  root: RootEntry,
  startPath: String,
  changeSets: Set<Long>,
  before: Boolean,
  processor: (Long, String?) -> Boolean,
) {
  val processContents = fun(changeSetId: Long, path: String): Boolean {
    if (!changeSets.contains(changeSetId)) return true
    val entry = root.findEntry(path)
    return processor(changeSetId, entry?.content?.getString(entry, gateway))
  }

  var path = startPath
  for (changeSet in changes) {

    ProgressManager.checkCanceled()
    if (Thread.currentThread().isInterrupted) throw ProcessCanceledException()
    if (!before && !processContents(changeSet.id, path)) break

    for (change in changeSet.changes.reversed()) {
      if (change is StructuralChange && change.affectsPath(path)) {
        change.revertOn(root, false)
        path = change.revertPath(path)
      }
    }

    if (before && !processContents(changeSet.id, path)) break
  }
}
