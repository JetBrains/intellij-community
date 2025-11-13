// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.ui.tree.TreeModelUpdateRequest
import com.intellij.ui.treeStructure.ProjectViewUpdateCause
import kotlin.concurrent.atomics.*
import kotlin.time.TimeSource

@OptIn(ExperimentalAtomicApi::class)
@Service(Level.PROJECT)
internal class ProjectViewPerformanceMonitor {
  companion object {
    @JvmStatic fun getInstance(project: Project): ProjectViewPerformanceMonitor = project.service()
  }
  
  private val requestId = AtomicLong(0L)

  fun beginUpdateAll(causes: Collection<ProjectViewUpdateCause>): TreeModelUpdateRequest {
    return Request(requestId.incrementAndFetch(), causes)
  }
  
  private class Request(val id: Long, causes: Collection<ProjectViewUpdateCause>) : TreeModelUpdateRequest {
    private val start = TimeSource.Monotonic.markNow()
    private val count = AtomicInt(0)
    private val finished = AtomicBoolean(false)
    
    init {
      LOG.debug { "[request $id] The entire PV is updated because $causes" }
    }
    
    override fun nodesLoaded(count: Int) {
      this.count.addAndFetch(count)
    }

    override fun finished() {
      if (!finished.compareAndSet(false, true)) return
      val elapsed = start.elapsedNow()
      LOG.debug { "[request $id] The update has finished in $elapsed, ${count.load()} nodes updated" }
    }
  }
}

private val LOG = logger<ProjectViewPerformanceMonitor>()
