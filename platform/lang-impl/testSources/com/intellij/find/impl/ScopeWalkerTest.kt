// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindModel
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class ScopeWalkerTest {
  companion object {
    private val project = projectFixture()
  }

  @Test
  fun `a directory outside the persistent VFS can be walked`(): Unit = timeoutRunBlocking {
    val child = LightVirtualFile("match.txt", "needle")
    val directory = object : LightVirtualFile("directory") {
      override fun isDirectory(): Boolean = true
      override fun getChildren(): Array<VirtualFile> = arrayOf(child)
    }
    val filter = CandidateFilter(
      fileMask = { true },
      excluded = { false },
      inModelScope = { true },
      inCustomScope = { true },
      coveredByReliable = { false },
      locateClassSources = false,
      sourceOf = { null },
    )
    val walker = ScopeWalker(
      findModel = FindModel(),
      project = project.get(),
      candidateFilter = filter,
      moduleToSearchIn = null,
      directoryToSearchIn = directory,
      withSubdirectories = true,
      directoryStandsForProject = false,
      customScope = null,
    )
    val plan = walker.roots(skipNonIndexableRoots = false)
    val candidates = ArrayList<VirtualFile>()
    val sink = object : ScopeWalker.Sink {
      override val isStopped: Boolean = false
      override fun addItem(item: WorkItem.Walk): Boolean = error("Unexpected nested directory")
      override fun addCandidate(file: VirtualFile, source: CandidateFilter.Source): Boolean = candidates.add(file)
    }

    readAction {
      for (item in plan.items) walker.expand(plan, item, sink)
    }

    assertThat(candidates).containsExactly(child)
  }
}
