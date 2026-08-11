// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.componentPanelTestAction

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.panel.ProgressPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.Alarm
import com.intellij.util.ui.UI
import javax.swing.JProgressBar

internal class ProgressTimerRequest(private val alarm: Alarm, private val progressBar: JProgressBar) : Runnable {
  override fun run() {
    if (canPlay()) {
      var value = progressBar.value + 1
      if (value > progressBar.maximum) {
        value = progressBar.minimum
      }
      progressBar.value = value

      ProgressPanel.getProgressPanel(progressBar)?.setCommentText(value.toString())
      alarm.addRequest(this, 200, ModalityState.any())
    }
  }

  private fun canPlay(): Boolean {
    val progressPanel = ProgressPanel.getProgressPanel(progressBar)
    return progressPanel != null && progressPanel.state == ProgressPanel.State.PLAYING
  }
}

internal class ProgressPanelResult(@JvmField val panel: DialogPanel, @JvmField val timerRequest: ProgressTimerRequest)

internal fun createProgressPanel(alarm: Alarm): ProgressPanelResult {
  val pb1 = JProgressBar(0, 100)
  val pb2 = JProgressBar(0, 100)

  val timerRequest = ProgressTimerRequest(alarm, pb1)

  val panel1 = UI.PanelFactory.panel(pb1)
    .withLabel("Label 1.1")
    .withCancel { alarm.cancelRequest(timerRequest) }
    .andCancelText("Stop")
    .createPanel()

  val panel2 = UI.PanelFactory.panel(pb2)
    .withLabel("Label 1.2")
    .withPause { println("Pause action #2") }
    .withResume { println("Resume action #2") }
    .createPanel()

  ProgressPanel.getProgressPanel(pb1)!!.setCommentText("Long long long long long long long text")
  ProgressPanel.getProgressPanel(pb2)!!.setCommentText("Short text")

  val pb3 = JProgressBar(0, 100)
  val pb4 = JProgressBar(0, 100)
  val panel3 = UI.PanelFactory.panel(pb3)
    .withLabel("Label 2.1").moveLabelLeft()
    .withCancel { println("Cancel action #3") }
    .createPanel()

  val panel4 = UI.PanelFactory.panel(pb4)
    .withTopSeparator()
    .withLabel("Label 2.2").moveLabelLeft()
    .withPause { println("Pause action #4") }
    .withResume { println("Resume action #4") }
    .createPanel()

  ProgressPanel.getProgressPanel(pb3)!!.setCommentText("Long long long long long long text")
  ProgressPanel.getProgressPanel(pb4)!!.setCommentText("Short text")

  val panel5 = UI.PanelFactory.panel(JProgressBar(0, 100))
    .withTopSeparator().withoutComment()
    .andCancelAsButton()
    .withCancel { println("Cancel action #11") }
    .createPanel()

  val dialogPanel = panel {
    row { cell(panel1).align(AlignX.FILL) }
    row { cell(panel2).align(AlignX.FILL) }
    row { cell(panel3).align(AlignX.FILL) }
    row { cell(panel4).align(AlignX.FILL) }
    row { cell(panel5).align(AlignX.FILL) }
  }

  return ProgressPanelResult(dialogPanel, timerRequest)
}
