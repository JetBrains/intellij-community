// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.statistics

import com.intellij.diff.merge.MergeStatisticsAggregator
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class MergeStatisticsCollectorTest {

  @TestDisposable
  lateinit var disposable: Disposable

  private fun collect(action: () -> Unit): List<LogEvent> =
    FUCollectorTestCase.collectLogEvents(disposable, action)

  private fun List<LogEvent>.dataOf(eventId: String): Map<String, Any> = single { it.event.id == eventId }.event.data

  @Test
  fun `magic wand pressed logs per-file result`() {
    val data = collect {
      MergeStatisticsCollector.logMagicWandPressed(null, MagicWandResult.PARTIALLY_RESOLVED)
    }.dataOf("magic.wand.pressed")
    assertEquals("PARTIALLY_RESOLVED", data["magic_wand_result"])
    assertEquals("ITERATIVE", data["flow"])
  }

  @Test
  fun `magic wand session logs best result`() {
    val data = collect {
      MergeStatisticsCollector.logMagicWandSession(null, MagicWandResult.FULLY_RESOLVED)
    }.dataOf("magic.wand.session.result")
    assertEquals("FULLY_RESOLVED", data["magic_wand_result"])
    assertEquals("ITERATIVE", data["flow"])
  }

  @Test
  fun `side applied on table`() {
    val data = collect {
      MergeStatisticsCollector.logSideAppliedOnTable(null,
                                                     MergeSide.LEFT,
                                                     SideAppliedFrom.CONTEXT_MENU,
                                                     selectedFilesCount = 2,
                                                     flow = MergeFlow.ITERATIVE)
    }.dataOf("side.applied")
    assertEquals("CONFLICTS_TABLE", data["source_place"])
    assertEquals("LEFT", data["side"])
    assertEquals("CONTEXT_MENU", data["applied_from"])
    assertEquals("2", data["selected_files_count"].toString())
    assertEquals("ITERATIVE", data["flow"])
  }

  @Test
  fun `side applied in viewer`() {
    val data = collect {
      MergeStatisticsCollector.logSideAppliedInViewer(null, MergeSide.RIGHT, SideAppliedFrom.BUTTON, MergeFlow.STANDALONE)
    }.dataOf("side.applied")
    assertEquals("MERGE_VIEWER", data["source_place"])
    assertEquals("RIGHT", data["side"])
    assertEquals("BUTTON", data["applied_from"])
    assertEquals("1", data["selected_files_count"].toString())
    assertEquals("STANDALONE", data["flow"])
  }

  @Test
  fun `revert used on table`() {
    val data = collect {
      MergeStatisticsCollector.logRevertUsedOnTable(null, selectedFilesCount = 2, usedOn = RevertUsedOn.BOTH)
    }.dataOf("revert.used")
    assertEquals("CONFLICTS_TABLE", data["source_place"])
    assertEquals("2", data["selected_files_count"].toString())
    assertEquals("BOTH", data["revert_used_on"])
    assertEquals("ITERATIVE", data["flow"])
  }

  @Test
  fun `revert used in viewer`() {
    val data = collect {
      MergeStatisticsCollector.logRevertUsedInViewer(null, RevertUsedOn.RESOLVED, MergeFlow.ONE_SHOT)
    }.dataOf("revert.used")
    assertEquals("MERGE_VIEWER", data["source_place"])
    assertEquals("RESOLVED", data["revert_used_on"])
    assertEquals("ONE_SHOT", data["flow"])
  }

  @Test
  fun `file opened`() {
    val data = collect {
      MergeStatisticsCollector.logFileOpened(null, times = 1, from = FileOpenedFrom.ROW_CLICK,
                                             usedOn = FileOpenedOn.UNRESOLVED, howOpened = FileOpenedHow.INTENTIONALLY,
                                             flow = MergeFlow.ONE_SHOT)
    }.dataOf("file.opened")
    assertEquals("1", data["times"].toString())
    assertEquals("ROW_CLICK", data["opened_from"])
    assertEquals("UNRESOLVED", data["open_used_on"])
    assertEquals("INTENTIONALLY", data["how_opened"])
    assertEquals("ONE_SHOT", data["flow"])
  }

  @Test
  fun `dialog closed`() {
    val data = collect {
      MergeStatisticsCollector.logDialogClosed(null, MergeFlow.ONE_SHOT)
    }.dataOf("dialog.closed")
    assertEquals("ONE_SHOT", data["flow"])
  }

  @Test
  fun `dialog accept`() {
    val data = collect {
      MergeStatisticsCollector.logDialogAccept(null, allReviewed = true)
    }.dataOf("dialog.accepted")
    assertEquals(true, data["all_reviewed"])
    assertEquals("ITERATIVE", data["flow"])
  }

  @Test
  fun `merge event logs flow`() {
    val data = collect {
      MergeStatisticsCollector.logMergeDialogEvent(null,
                                                   MergeAction.APPLY,
                                                   confirmationShown = true,
                                                   confirmationAccepted = false,
                                                   byEsc = false,
                                                   flow = MergeFlow.ONE_SHOT)
    }.dataOf("merge.event")
    assertEquals("APPLY", data["action"])
    assertEquals("ONE_SHOT", data["flow"])
  }

  @Test
  fun `merge event logs save and close action`() {
    val data = collect {
      MergeStatisticsCollector.logMergeDialogEvent(null,
                                                   MergeAction.SAVE_AND_CLOSE,
                                                   confirmationShown = false,
                                                   confirmationAccepted = false,
                                                   byEsc = false,
                                                   flow = MergeFlow.ITERATIVE)
    }.dataOf("merge.event")
    assertEquals("SAVE_AND_CLOSE", data["action"])
    assertEquals("ITERATIVE", data["flow"])
  }

  @Test
  fun `file merged logs saved result`() {
    val aggregator = MergeStatisticsAggregator(0, 0, 0, 0, null)
    val data = collect {
      MergeStatisticsCollector.logMergeFinished(null, MergeStatisticsCollector.MergeResult.SAVED,
                                                MergeResultSource.DIALOG_BUTTON, aggregator, MergeFlow.ITERATIVE)
    }.dataOf("file.merged")
    assertEquals("SAVED", data["result"])
    assertEquals("ITERATIVE", data["flow"])
  }
}
