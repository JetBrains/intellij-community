// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.getOrCreateFrameContentPaintedDeferred
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.IntelliJTracer
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.recordStartupSpan

private val startupScope = Scope("startup")

/** The tracer for a start-up span of suspend code. */
internal val welcomeScreenStartupTracer: IntelliJTracer by lazy { TelemetryManager.getSimpleTracer(startupScope) }

/** The tracer for a start-up span of synchronous code. */
internal val welcomeScreenStartupSpanTracer: IJTracer by lazy { TelemetryManager.getTracer(startupScope) }

/**
 * Records the first paint of the welcome screen.
 *
 * The left panel calls [leftPainted] when it first paints its actions.
 * [leftPainted] also completes [getOrCreateFrameContentPaintedDeferred], so the post-startup activities of the project can start.
 * The right tab calls [rightPainted] when it first paints its body.
 * When both sides painted, the tracker adds the instant event `welcome screen painted`.
 * It also adds the span `welcome screen painted` from the IDE start to that time.
 * Each side reports only once, so a later paint does no work.
 * Call both functions on the EDT.
 */
@Service(Service.Level.PROJECT)
internal class WelcomeScreenPaintTracker(private val project: Project) {
  @Volatile
  private var isLeftPainted = false

  @Volatile
  private var isRightPainted = false

  fun leftPainted() {
    if (isLeftPainted) {
      return
    }
    isLeftPainted = true
    project.getOrCreateFrameContentPaintedDeferred().complete(Unit)
    if (isRightPainted) {
      reportPainted()
    }
  }

  fun rightPainted() {
    if (isRightPainted) {
      return
    }
    isRightPainted = true
    if (isLeftPainted) {
      reportPainted()
    }
  }

  private fun reportPainted() {
    StartUpMeasurer.addInstantEvent(PAINTED_NAME)
    recordStartupSpan(PAINTED_NAME, StartUpMeasurer.getStartTime(), System.nanoTime())
  }

  companion object {
    private const val PAINTED_NAME = "welcome screen painted"

    fun getInstance(project: Project): WelcomeScreenPaintTracker = project.service()
  }
}
