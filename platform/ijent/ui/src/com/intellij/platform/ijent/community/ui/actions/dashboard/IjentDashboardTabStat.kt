// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.actions.dashboard

import com.intellij.openapi.project.Project
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.community.ui.actions.IjentImplBundle
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.awaitCancellation
import java.awt.Font
import javax.swing.JComponent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

internal class IjentDashboardTabStat : IjentDashboardTab {
  override val name: String
    get() = IjentImplBundle.message("tab.title.ijent.dashboard.stat")

  override fun createComponent(projects: List<Project>, ijentApi: IjentApi, ijentSession: IjentSession, parentComponent: JComponent?): JComponent {
    val stat = IjentStatCounter()
    val result = IjentStatDashboard(stat).launchOnShow()
    val scopeComponent = parentComponent ?: result
    scopeComponent.launchOnShow("listen statistics") {
      stat.process(ijentSession.eventBus) {
        awaitCancellation()
      }
    }
    return result
  }
}

internal class IjentStatDashboard(private val stat: IjentStatCounter) {
  private val statTextArea = createReadOnlyMonoViewer(10, 80)
  val component: JComponent = panel {
    row { cell(JBScrollPane(statTextArea)).resizableColumn().align(Align.FILL) }.resizableRow()
  }
  suspend fun processUpdates(): Nothing {
    stat.snapshotFlow(100.milliseconds).collect { dataMap ->
      statTextArea.text = dataMap.entries.sortedBy { it.key }.joinToString("\n") { (methodName, v) ->
        val currentNanoTime = System.nanoTime()
        val averageText = if (v.totalCallsFinished > 0) (v.totalCallsDuration / v.totalCallsFinished).toShortString() else ""
        val lastOpDuration = if (v.notFinishedOperationsStartNanos.isNotEmpty()) {
          (currentNanoTime - v.notFinishedOperationsStartNanos.first()).nanoseconds.toShortString()
        }
        else {
          v.lastOperationDurationNanos?.nanoseconds?.toShortString() ?: ""
        }
        val isPendingMark = if (v.notFinishedOperationsStartNanos.isNotEmpty()) " ⟳" else "  "
        val lastOpFinished = v.lastOperationFinishedNanos?.let { currentNanoTime - it }?.nanoseconds?.toShortString() ?: "N/A"
        "${methodName.padStart(25)}:" +
        "${v.totalCallsFinished.toString().padStart(5)} total, " +
        "${averageText.padStart(6)} avg," +
        isPendingMark +
        "${lastOpDuration.padStart(6)} last" +
        "${lastOpFinished.padStart(6)} ago."
      }
    }
    error("unreachable")
  }
  fun launchOnShow(): JComponent {
    component.launchOnShow("display statistics") {
      processUpdates()
    }
    return component
  }
}

private fun Duration.toShortString(): String {
  for (unit in DurationUnit.entries.asReversed()) {
    val value = this.toDouble(unit)
    if (value >= 1.0) {
      val decimals = if (value >= 10.0) 0 else 1
      return this.toString(unit, decimals)
    }
  }
  return this.toString(DurationUnit.NANOSECONDS, 0)
}

internal fun createReadOnlyMonoViewer(rows: Int, cols: Int): JBTextArea =
  JBTextArea(rows, cols).apply {
    isEditable = false
    lineWrap = false
    border = JBUI.Borders.empty()
    background = UIUtil.getPanelBackground()
    font = JBFont.create(Font(Font.MONOSPACED, Font.PLAIN, JBFont.label().size))
    caret.blinkRate = 0
  }