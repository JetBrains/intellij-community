// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexEx
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.psi.tree.StubFileElementType
import kotlin.test.assertEquals

class StubIndexPerFileElementTypeModificationTrackerTestHelper() {
  private val lastSeenModCounts = mutableMapOf<StubFileElementType<*>, Long>()

  fun setUp() {
    lastSeenModCounts.clear()
  }

  fun ensureStubIndexUpToDate(project: Project) {
    FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, GlobalSearchScope.allScope(project))
  }

  fun getModCount(type: StubFileElementType<*>) = (StubIndex.getInstance() as StubIndexEx)
    .getPerFileElementTypeModificationTracker(type).modificationCount

  fun initModCounts(vararg types: StubFileElementType<*>) {
    types.map {
      lastSeenModCounts[it] = getModCount(it)
    }
  }

  fun checkModCountIncreasedAtLeast(type: StubFileElementType<*>, minInc: Int) {
    val modCount = getModCount(type)
    assert(modCount >= lastSeenModCounts[type]!! + minInc)
    lastSeenModCounts[type] = modCount
  }

  fun checkModCountHasChanged(type: StubFileElementType<*>) = checkModCountIncreasedAtLeast(type, 1)

  fun checkModCountIsSame(type: StubFileElementType<*>) {
    val modCount = getModCount(type)
    assertEquals(lastSeenModCounts[type]!!, modCount)
  }
}