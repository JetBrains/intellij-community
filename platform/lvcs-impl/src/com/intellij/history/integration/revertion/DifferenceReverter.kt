// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration.revertion

import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.core.Paths
import com.intellij.history.core.revisions.Difference
import com.intellij.history.core.revisions.Revision
import com.intellij.history.core.tree.Entry
import com.intellij.history.integration.IdeaGateway
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException

class DifferenceReverter(
  project: Project,
  gateway: IdeaGateway,
  private val diffs: List<Difference>,
  commandName: () -> String,
) : Reverter(project, gateway, commandName) {
  constructor(
    project: Project,
    gateway: IdeaGateway,
    diffs: List<Difference>,
    leftRevision: Revision,
  ) : this(project, gateway, diffs, { getRevertCommandName(leftRevision) })

  @Deprecated("Facade is unused")
  constructor(
    project: Project,
    @Suppress("unused") facade: LocalHistoryFacade?,
    gateway: IdeaGateway,
    diffs: List<Difference>,
    leftRevision: Revision,
  ) : this(project, gateway, diffs, { getRevertCommandName(leftRevision) })

  override val filesToClearROStatus: List<VirtualFile>
    get() =
      buildSet {
        diffs.forEach { diff ->
          diff.left?.let { gateway.findVirtualFile(it.getPath()) }?.let {
            add(it)
          }
          diff.right?.let { gateway.findVirtualFile(it.getPath()) }?.let {
            add(it)
          }
        }
      }.toList()

  @Throws(IOException::class)
  override fun doRevert() {
    doRevert(true)
  }

  @Throws(IOException::class)
  fun doRevert(revertContentChanges: Boolean) {
    val vetoedFiles = HashSet<String>()

    for (diff in diffs.asReversed()) {
      val leftEntry = diff.left
      val rightEntry = diff.right

      if (leftEntry == null) {
        revertCreation(rightEntry!!, vetoedFiles)
        continue
      }

      vetoedFiles.add(leftEntry.getPath())
      if (rightEntry == null) {
        revertDeletion(leftEntry)
        continue
      }

      val file = gateway.findOrCreateFileSafely(rightEntry.getPath(), rightEntry.isDirectory)
      revertRename(leftEntry, file)
      if (revertContentChanges) {
        revertContentChange(leftEntry, file)
      }
    }
  }

  @Throws(IOException::class)
  private fun revertCreation(entry: Entry, vetoedFiles: Set<String>) {
    val path = entry.getPath()
    for (each in vetoedFiles) {
      if (Paths.isParent(path, each)) return
    }

    gateway.findVirtualFile(path)?.delete(this)
  }

  @Throws(IOException::class)
  private fun revertDeletion(entry: Entry) {
    val file = gateway.findOrCreateFileSafely(entry.getPath(), entry.isDirectory)
    if (entry.isDirectory) return
    setContent(entry, file)
  }

  @Throws(IOException::class)
  private fun revertRename(entry: Entry, file: VirtualFile) {
    val oldName = entry.getName()
    if (oldName != file.getName()) {
      val existing = file.getParent().findChild(oldName)
      existing?.delete(this)
      file.rename(this, oldName)
    }
  }

  companion object {
    @Throws(IOException::class)
    private fun revertContentChange(entry: Entry, file: VirtualFile) {
      if (entry.isDirectory) return
      if (file.getTimeStamp() != entry.getTimestamp()) {
        setContent(entry, file)
      }
    }

    @Throws(IOException::class)
    private fun setContent(entry: Entry, file: VirtualFile) {
      val content = entry.content
      if (!content.isAvailable()) return
      file.setBinaryContent(content.getBytes())
    }
  }
}
