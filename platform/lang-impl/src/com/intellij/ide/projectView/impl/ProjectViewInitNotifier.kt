@file:Internal
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicBoolean

private val projectViewInit: Scope = Scope("projectViewInit")

@Service(Service.Level.PROJECT)
internal class ProjectViewInitNotifier(private val project: Project) {
  private val startedOccurred = AtomicBoolean()
  private val cachedNodesLoadedOccurred = AtomicBoolean()
  private val completedOccurred = AtomicBoolean()

  private val tracingHelper = TracingHelper()

  fun initStarted() {
    if (!startedOccurred.compareAndSet(false, true)) return
    LOG.info("Project View initialization started")
    tracingHelper.initStarted()
    project.messageBus.syncPublisher(ProjectViewListener.TOPIC).initStarted()
  }

  fun initCachedNodesLoaded() {
    if (!cachedNodesLoadedOccurred.compareAndSet(false, true)) return
    tracingHelper.initCachedNodesLoaded()
    LOG.info("Project View cached nodes loaded")
    project.messageBus.syncPublisher(ProjectViewListener.TOPIC).initCachedNodesLoaded()
  }

  fun initCompleted() {
    if (!completedOccurred.compareAndSet(false, true)) return
    tracingHelper.initCompleted()
    LOG.info("Project View initialization completed")
    project.messageBus.syncPublisher(ProjectViewListener.TOPIC).initCompleted()
  }

  private inner class TracingHelper {
    private val mainSpan = Ref<Span>()
    private val cachedNodesSpan = Ref<Span>()
    private val projectViewInitTracer: Tracer = TelemetryManager.getInstance().getTracer(projectViewInit)

    fun initStarted() {
      val span = projectViewInitTracer.spanBuilder("projectViewInit").startSpan().also { mainSpan.set(it) }
      cachedNodesSpan.set(
        projectViewInitTracer.spanBuilder("projectViewInit#cachedNodesLoaded").setParent(Context.current().with(span)).startSpan()
      )
    }

    fun initCachedNodesLoaded() {
      cachedNodesSpan.get()?.end()
    }

    fun initCompleted() {
      mainSpan.get()?.end()
    }
  }
}

private val LOG = logger<ProjectViewInitNotifier>()
