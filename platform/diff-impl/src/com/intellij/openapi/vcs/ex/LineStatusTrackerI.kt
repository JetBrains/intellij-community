// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.*

/**
 * Tracker state can be updated without taking an Application writeLock.
 * So Application readLock does not guarantee that 2 [getRanges] calls will return same results.
 * Use [readLock] when consistency is needed.
 *
 * Pay attention to [isValid] and [isOperational].
 */
interface LineStatusTrackerI<out R : Range> {
  val project: Project?
  val disposable: Disposable

  val document: Document
  val vcsDocument: Document

  /**
   * File associated with [document]
   */
  val virtualFile: VirtualFile?

  val isReleased: Boolean

  /**
   * Whether [vcsDocument] content is successfully loaded and tracker is not [isReleased].
   */
  fun isOperational(): Boolean

  /**
   * Whether internal state is synchronized with [document] and [vcsDocument].
   * While `false`, most of the methods in this interface return `null` or silently do nothing.
   *
   * Returns `false` if tracker is not [isOperational] or is frozen [doFrozen].
   */
  fun isValid(): Boolean

  /**
   * Changed line ranges between documents.
   *
   * Requires an Application readLock.
   */
  fun getRanges(): List<R>?

  fun getRangesForLines(lines: BitSet): List<R>?
  fun getRangeForLine(line: Int): R?

  fun getNextRange(line: Int): R?
  fun getPrevRange(line: Int): R?
  fun findRange(range: Range): R?


  fun isLineModified(line: Int): Boolean
  fun isRangeModified(startLine: Int, endLine: Int): Boolean

  fun transferLineFromVcs(line: Int, approximate: Boolean): Int
  fun transferLineToVcs(line: Int, approximate: Boolean): Int


  @RequiresEdt
  fun rollbackChanges(range: Range)

  /**
   * Modify [document] to match [vcsDocument] content for the passed line ranges.
   *
   * @param lines line numbers in [document]
   * @see com.intellij.diff.util.DiffUtil.getSelectedLines
   */
  @RequiresEdt
  fun rollbackChanges(lines: BitSet)

  /**
   * Prevent internal tracker state from being updated for the time being.
   * It will be synchronized once when the [task] is finished.
   *
   * @see com.intellij.codeInsight.actions.VcsFacade.runHeavyModificationTask
   */
  @RequiresEdt
  fun doFrozen(task: Runnable)

  /**
   * Run a task under tracker own lock (not to be confused with Application readLock).
   *
   * [task] should not take Application readLock inside.
   */
  fun <T> readLock(task: () -> T): T
}
