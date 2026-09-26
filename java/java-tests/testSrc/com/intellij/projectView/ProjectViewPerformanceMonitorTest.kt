// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.projectView

import com.intellij.ide.projectView.impl.ProjectViewUpdatePerformanceCalculator
import com.intellij.ide.projectView.impl.ProjectViewUpdateReport
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.ui.treeStructure.ProjectViewUpdateCause
import org.junit.jupiter.api.Test
import kotlin.time.AbstractLongTimeSource
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

class ProjectViewPerformanceMonitorTest {
  @Test
  fun `no updates`() = withFixture {
    report()
    assertThat(reports).isEmpty()
  }

  @Test
  fun `no finished updates`() = withFixture {
    val cause1 = SOME_CAUSE
    val cause2 = SOME_OTHER_CAUSE
    requestStarted(1, cause1)
    requestStarted(2, cause2)
    nodesLoaded(1, 123)
    report()
    assertThat(reports).hasSize(1)
    assertLastReportStats(started = 2, active = 2, finished = 0)
    assertThat(reports.single().updateCauseReports).isEmpty()
    assertThat(reports.single().stuckRequestReports).isEmpty()
  }

  @Test
  fun `finished update`() = withFixture {
    val cause1 = SOME_CAUSE
    val cause2 = SOME_OTHER_CAUSE
    requestStarted(1, cause1)
    requestStarted(2, cause2)
    nodesLoaded(1, 123)
    nodesLoaded(2, 456)
    time = 1000
    requestFinished(1)
    report()
    assertThat(reports).hasSize(1)
    assertLastReportStats(started = 2, active = 1, finished = 1)
    assertThat(reports.single().stuckRequestReports).isEmpty()
    assertThat(reports.single().updateCauseReports).containsOnlyKeys(cause1)
    assertLastReportCause(cause1, completed = 1, loaded = 123, spent = 1000)
  }

  @Test
  fun `finished several requests`() = withFixture {
    val cause = SOME_CAUSE
    requestStarted(1, cause)
    nodesLoaded(1, 123)
    time = 300
    requestFinished(1)
    time = 600
    requestStarted(2, cause)
    time = 800
    nodesLoaded(2, 456)
    time = 1000
    requestFinished(2)
    report()
    assertThat(reports).hasSize(1)
    assertLastReportStats(started = 2, active = 0, finished = 2)
    assertThat(reports.single().stuckRequestReports).isEmpty()
    assertThat(reports.single().updateCauseReports).containsOnlyKeys(cause)
    assertLastReportCause(cause, completed = 2, loaded = 123 + 456, spent = 300 + (1000 - 600))
  }

  @Test
  fun `finished several updates`() = withFixture {
    val cause1 = SOME_CAUSE
    val cause2 = SOME_OTHER_CAUSE
    requestStarted(1, cause1)
    requestStarted(2, cause2)
    nodesLoaded(1, 123)
    nodesLoaded(2, 456)
    time = 1000
    requestFinished(1)
    report()
    assertLastReportStats(started = 2, active = 1, finished = 1)
    time = 2000
    requestFinished(2)
    report()
    assertLastReportStats(started = 0, active = 0, finished = 1)
    assertThat(reports).hasSize(2)
    assertThat(reports.last().stuckRequestReports).isEmpty()
    assertThat(reports.last().updateCauseReports).containsOnlyKeys(cause2)
    assertLastReportCause(cause2, completed = 1, loaded = 456, spent = 2000)
  }

  @Test
  fun `several causes for one request`() = withFixture {
    val cause1 = SOME_CAUSE
    val cause2 = SOME_OTHER_CAUSE
    requestStarted(1, listOf(cause1, cause2))
    nodesLoaded(1, 123)
    time = 200
    requestStarted(2, cause2)
    nodesLoaded(2, 456)
    time = 400
    requestFinished(1)
    time = 900
    requestFinished(2)
    report()
    assertLastReportStats(started = 2, active = 0, finished = 2)
    assertThat(reports).hasSize(1)
    assertThat(reports.last().stuckRequestReports).isEmpty()
    assertThat(reports.last().updateCauseReports).containsOnlyKeys(cause1, cause2)
    assertLastReportCause(cause1, completed = 1, loaded = 123, spent = 400)
    assertLastReportCause(cause2, completed = 2, loaded = 123 + 456, spent = 900)
  }

  @Test
  fun `stuck updates`() = withFixture {
    val cause1 = SOME_CAUSE
    val cause2 = SOME_OTHER_CAUSE
    time = 100
    requestStarted(1, cause1)
    time = 200
    nodesLoaded(1, 123)
    time = 1000
    report()
    assertThat(reports).hasSize(1)
    assertLastReportStats(started = 1, active = 1, finished = 0)
    assertThat(reports.last().stuckRequestReports).isEmpty()
    time = 1100
    nodesLoaded(1, 345)
    time = 1200
    requestStarted(2, cause2)
    time = 1300
    nodesLoaded(2, 678)
    time = 2000
    report()
    assertThat(reports).hasSize(2)
    assertLastReportStats(started = 1, active = 2, finished = 0)
    assertThat(reports.last().updateCauseReports).isEmpty()
    assertThat(reports.last().stuckRequestReports).containsOnlyKeys(cause1)
    assertLastReportStuckRequests(cause1, requests = 1, loaded = 123 + 345, spent = 1900)
  }

  @Test
  fun `two requests, one stuck`() = withFixture {
    val cause = SOME_CAUSE
    time = 100
    requestStarted(1, cause)
    time = 200
    nodesLoaded(1, 123)
    requestFinished(1)
    time = 300
    requestStarted(2, cause)
    nodesLoaded(2, 456)
    time = 1000
    report()
    assertThat(reports).hasSize(1)
    assertLastReportStats(started = 2, active = 1, finished = 1)
    assertThat(reports.last().stuckRequestReports).isEmpty()
    assertLastReportCause(cause, completed = 1, loaded = 123, spent = 100)
    time = 2000
    report()
    assertThat(reports).hasSize(2)
    assertLastReportStats(started = 0, active = 1, finished = 0)
    assertThat(reports.last().updateCauseReports).isEmpty()
    assertThat(reports.last().stuckRequestReports).containsOnlyKeys(cause)
    assertLastReportStuckRequests(cause, requests = 1, loaded = 456, spent = 1700)
  }

  @Test
  fun `stuck updates for long time`() = withFixture {
    val cause1 = SOME_CAUSE
    val cause2 = SOME_OTHER_CAUSE
    time = 100
    requestStarted(1, cause1)
    time = 200
    nodesLoaded(1, 123)
    time = 1000
    report()
    assertThat(reports).hasSize(1)
    assertLastReportStats(started = 1, active = 1, finished = 0)
    time = 1100
    nodesLoaded(1, 345)
    time = 1200
    requestStarted(2, cause2)
    time = 1300
    nodesLoaded(2, 678)
    time = 2000
    report()
    assertLastReportStats(started = 1, active = 2, finished = 0)
    time = 3000
    report()
    assertThat(reports).hasSize(3)
    assertLastReportStats(started = 0, active = 2, finished = 0)
    assertThat(reports.last().updateCauseReports).isEmpty()
    assertThat(reports.last().stuckRequestReports).containsOnlyKeys(cause1, cause2)
    assertLastReportStuckRequests(cause1, requests = 1, loaded = 123 + 345, spent = 2900)
    assertLastReportStuckRequests(cause2, requests = 1, loaded = 678, spent = 1800)
  }

  private fun withFixture(block: Fixture.() -> Unit) {
    val fixture = Fixture()
    fixture.block()
  }

  private class Fixture {
    val reports = mutableListOf<ProjectViewUpdateReport>()
    private val sut = ProjectViewUpdatePerformanceCalculator { reports += it }
    var time = 0L
    private val timeSource = object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
      override fun read(): Long = time
    }

    fun requestStarted(id: Long, cause: ProjectViewUpdateCause) {
      sut.requestStarted(id, listOf(cause), timeSource.markNow())
    }

    fun requestStarted(id: Long, causes: List<ProjectViewUpdateCause>) {
      sut.requestStarted(id, causes, timeSource.markNow())
    }

    fun nodesLoaded(id: Long, loadedCount: Int) {
      sut.nodesLoaded(id, loadedCount)
    }

    fun requestFinished(id: Long) {
      sut.requestFinished(id, timeSource.markNow())
    }

    fun report() {
      sut.reportSample(timeSource.markNow())
    }

    fun assertLastReportStats(started: Int, active: Int, finished: Int) {
      val report = reports.last()
      assertThat(report.stats.startedRequests).`as`("started").isEqualTo(started)
      assertThat(report.stats.activeRequests).`as`("active").isEqualTo(active)
      assertThat(report.stats.finishedRequests).`as`("finished").isEqualTo(finished)
    }

    fun assertLastReportCause(cause: ProjectViewUpdateCause, completed: Int, loaded: Int, spent: Int) {
      val report = reports.last().updateCauseReports.getValue(cause)
      assertThat(report.completedRequests).`as`("completed").isEqualTo(completed)
      assertThat(report.loadedNodeCount).`as`("loaded").isEqualTo(loaded)
      assertThat(report.timeSpent).`as`("spent").isEqualTo(spent.milliseconds)
    }

    fun assertLastReportStuckRequests(cause: ProjectViewUpdateCause, requests: Int, loaded: Int, spent: Int) {
      val report = reports.last().stuckRequestReports.getValue(cause)
      assertThat(report.requestCount).`as`("requests").isEqualTo(requests)
      assertThat(report.loadedNodeCount).`as`("loaded").isEqualTo(loaded)
      assertThat(report.stuckFor).`as`("spent").isEqualTo(spent.milliseconds)
    }
  }
}

private val SOME_CAUSE = ProjectViewUpdateCause.SETTINGS
private val SOME_OTHER_CAUSE = ProjectViewUpdateCause.ACTION
