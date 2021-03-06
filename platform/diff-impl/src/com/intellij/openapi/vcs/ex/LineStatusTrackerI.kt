// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

interface LineStatusTrackerI<out R : Range> {
  val project: Project?
  val disposable: Disposable

  val document: Document
  val vcsDocument: Document
  val virtualFile: VirtualFile?

  val isReleased: Boolean
  fun isOperational(): Boolean
  fun isValid(): Boolean


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


  fun rollbackChanges(range: Range)
  fun rollbackChanges(lines: BitSet)


  fun doFrozen(task: Runnable)
  fun <T> readLock(task: () -> T): T
}
