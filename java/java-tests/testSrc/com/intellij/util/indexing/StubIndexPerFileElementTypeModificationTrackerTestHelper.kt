// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexEx
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.psi.tree.IFileElementType
import kotlin.test.assertEquals

class StubIndexPerFileElementTypeModificationTrackerTestHelper() {
  private val lastSeenModCounts = mutableMapOf<IFileElementType, Long>()

  fun setUp() {
    lastSeenModCounts.clear()
  }

  /**
   * call to propagate information about file element types into indexes
   */
  fun ensureStubIndexUpToDate(project: Project) {
    FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, GlobalSearchScope.allScope(project))
  }

  fun getModCount(type: IFileElementType): Long = (StubIndex.getInstance() as StubIndexEx)
    .getPerFileElementTypeModificationTracker(type).modificationCount

  fun initModCounts(vararg types: IFileElementType) {
    types.map {
      lastSeenModCounts[it] = getModCount(it)
    }
  }

  fun checkModCountIncreasedAtLeast(type: IFileElementType, minInc: Int) {
    val modCount = getModCount(type)
    assert(modCount >= lastSeenModCounts[type]!! + minInc)
    lastSeenModCounts[type] = modCount
  }

  fun checkModCountHasChanged(type: IFileElementType): Unit = checkModCountIncreasedAtLeast(type, 1)

  fun checkModCountIsSame(type: IFileElementType) {
    val modCount = getModCount(type)
    assertEquals(lastSeenModCounts[type]!!, modCount)
  }
}