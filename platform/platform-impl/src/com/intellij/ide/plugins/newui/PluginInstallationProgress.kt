// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.ApiStatus

internal fun ProgressIndicator.withDownloadProgressSink(progressSink: PluginInstallationProgressSink): ProgressIndicator {
  val indicator = this
  return object : ProgressIndicator by indicator {
    override fun setFraction(fraction: Double) {
      indicator.fraction = fraction
      progressSink.downloadProgressChanged(fraction)
    }

    override fun setIndeterminate(indeterminate: Boolean) {
      indicator.isIndeterminate = indeterminate
      progressSink.downloadProgressChanged(if (indeterminate) null else indicator.fraction)
    }

    override fun popState() {
      indicator.popState()
      progressSink.downloadProgressChanged(if (indicator.isIndeterminate) null else indicator.fraction)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun startNonCancelableSection() {
      indicator.startNonCancelableSection()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun finishNonCancelableSection() {
      indicator.finishNonCancelableSection()
    }
  }
}

internal fun PluginInstallationProgressSink.withDownloadProgressIndicator(
  indicator: ProgressIndicator,
): PluginInstallationProgressSink {
  val delegate = this
  return object : PluginInstallationProgressSink {
    override fun dependenciesScheduled(dependencies: List<PluginUiModel>) {
      delegate.dependenciesScheduled(dependencies)
    }

    override fun downloadProgressChanged(fraction: Double?) {
      if (fraction == null) {
        indicator.isIndeterminate = true
      }
      else {
        indicator.isIndeterminate = false
        indicator.fraction = fraction
      }
      delegate.downloadProgressChanged(fraction)
    }
  }
}

@ApiStatus.Internal
fun PluginInstallationProgressSink.withWholePercentDownloadProgress(): PluginInstallationProgressSink {
  val delegate = this
  return object : PluginInstallationProgressSink {
    private var hasReportedProgress = false
    private var lastReportedPercent: Int? = null

    override fun dependenciesScheduled(dependencies: List<PluginUiModel>) {
      delegate.dependenciesScheduled(dependencies)
    }

    override fun downloadProgressChanged(fraction: Double?) {
      val percent = fraction?.let { (it * 100).toInt() }
      if (hasReportedProgress && percent == lastReportedPercent) return

      hasReportedProgress = true
      lastReportedPercent = percent
      delegate.downloadProgressChanged(fraction)
    }
  }
}
