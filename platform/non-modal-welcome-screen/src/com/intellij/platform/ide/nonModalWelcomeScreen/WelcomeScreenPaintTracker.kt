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
 * The first paint of each side adds the instant event and the span `welcome left panel painted` or `welcome right tab painted`.
 * When both sides painted, the tracker adds the instant event and the span `welcome screen painted`.
 * Each span runs from the IDE start to the paint.
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
    reportPainted(LEFT_PAINTED_NAME)
    if (isRightPainted) {
      reportPainted(PAINTED_NAME)
    }
  }

  fun rightPainted() {
    if (isRightPainted) {
      return
    }
    isRightPainted = true
    reportPainted(RIGHT_PAINTED_NAME)
    if (isLeftPainted) {
      reportPainted(PAINTED_NAME)
    }
  }

  private fun reportPainted(name: String) {
    StartUpMeasurer.addInstantEvent(name)
    recordStartupSpan(name, StartUpMeasurer.getStartTime(), System.nanoTime())
  }

  companion object {
    private const val PAINTED_NAME = "welcome screen painted"
    private const val LEFT_PAINTED_NAME = "welcome left panel painted"
    private const val RIGHT_PAINTED_NAME = "welcome right tab painted"

    fun getInstance(project: Project): WelcomeScreenPaintTracker = project.service()
  }
}
