/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.history.core

import com.intellij.history.ByteContent
import com.intellij.history.core.changes.*
import com.intellij.history.core.revisions.ChangeRevision
import com.intellij.history.core.revisions.RecentChange
import com.intellij.history.core.tree.Entry
import com.intellij.history.core.tree.RootEntry
import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.regex.Pattern

open class LocalHistoryFacade(private val changeList: ChangeList) {
  private val listeners: MutableList<Listener> = ContainerUtil.createLockFreeCopyOnWriteList()

  @get:TestOnly
  val changeListInTests get() = changeList
  internal val changes: Iterable<ChangeSet> get() = changeList.iterChanges()

  fun beginChangeSet() {
    changeList.beginChangeSet()
  }

  fun forceBeginChangeSet() {
    if (changeList.forceBeginChangeSet()) {
      fireChangeSetFinished()
    }
  }

  fun endChangeSet(name: @NlsContexts.Label String?) {
    if (changeList.endChangeSet(name)) {
      fireChangeSetFinished()
    }
  }

  fun created(path: String, isDirectory: Boolean) {
    addChange(if (isDirectory) CreateDirectoryChange(changeList.nextId(), path)
              else CreateFileChange(changeList.nextId(), path))
  }

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

  fun putSystemLabel(name: @NlsContexts.Label String, projectId: String, color: Int): LabelImpl {
    return putLabel(PutSystemLabelChange(changeList.nextId(), name, projectId, color))
  }

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

  fun getRecentChanges(root: RootEntry): List<RecentChange> {
    val result = mutableListOf<RecentChange>()

    for (c in changeList.iterChanges()) {
      if (c.isContentChangeOnly) continue
      if (c.isLabelOnly) continue
      if (c.name == null) continue

      val before = ChangeRevision(this, root, "", c, true)
      val after = ChangeRevision(this, root, "", c, false)
      result.add(RecentChange(before, after))
      if (result.size >= 20) break
    }

    return result
  }

  fun accept(v: ChangeVisitor) = changeList.accept(v)

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

  private fun fireChangeSetFinished() {
    for (each in listeners) {
      each.changeSetFinished()
    }
  }

  abstract class Listener {
    open fun changeAdded(c: Change) = Unit
    open fun changeSetFinished() = Unit
  }
}

@ApiStatus.Experimental
fun LocalHistoryFacade.collectChanges(projectId: String?, startPath: String, patternString: String?, consumer: (ChangeSet) -> Unit) {
  val pattern = patternString?.let { Pattern.compile(NameUtil.buildRegexp(it, 0, true, true), Pattern.CASE_INSENSITIVE) }

  val processedChangesSets = mutableSetOf<Long>()
  val processChangeSet = fun(changeSet: ChangeSet, change: Change, changePath: String) {
    if (!processedChangesSets.contains(changeSet.id) && change.matches(projectId, changePath, pattern)) {
      processedChangesSets.add(changeSet.id)
      consumer(changeSet)
    }
  }

  var path = startPath
  var pathExists = true
  for (changeSet in changes) {
    for (change in changeSet.changes.reversed()) {
      if (!pathExists) {
        if (change is StructuralChange) path = change.revertPath(path)
        if (change is DeleteChange && change.isDeletionOf(path)) {
          processChangeSet(changeSet, change, path)
          pathExists = true
        }
      } else {
        processChangeSet(changeSet, change, path)
        if (change is StructuralChange) path = change.revertPath(path)
        if (change is CreateEntryChange && change.isCreationalFor(path)) pathExists = false
      }
    }
  }
}

fun Change.matches(projectId: String?, path: String, pattern: Pattern?): Boolean {
  if (!affectsPath(path) && !affectsProject(projectId)) return false
  if (pattern != null && !affectsMatching(pattern)) return false
  return true
}

internal fun LocalHistoryFacade.processContents(gateway: IdeaGateway,
                                                root: RootEntry,
                                                startPath: String,
                                                changeSets: Set<Long>,
                                                before: Boolean,
                                                processor: (Long, String?) -> Boolean) {
  var path: String? = startPath
  accept(object : ChangeVisitor() {
    private fun processContent(changeSetId: Long): Boolean {
      if (!changeSets.contains(changeSetId)) return true
      val entry = root.findEntry(path)
      return processor(changeSetId, entry?.content?.getString(entry, gateway))
    }

    override fun begin(c: ChangeSet) {
      ProgressManager.checkCanceled()
      if (Thread.currentThread().isInterrupted) throw ProcessCanceledException()
      if (!before && !processContent(c.id)) stop()
    }

    @Throws(StopVisitingException::class)
    override fun end(c: ChangeSet) {
      if (before && !processContent(c.id)) stop()
    }

    override fun visit(c: StructuralChange) {
      if (c.affectsPath(path)) {
        c.revertOn(root, false)
        path = c.revertPath(path)
      }
    }
  })
}