// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar.components

import com.intellij.execution.runToolbar.RunToolbarProcess
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JPanel

class ProcessesByType : JPanel() {

  private var onlyCount = true
  private var environment = emptyList<ExecutionEnvironment>()
  private var process = RunToolbarProcess.getProcesses()[0]


  private val settingLabel = object : TrimmedMiddleLabel() {
    override fun getFont(): Font {
      return UIUtil.getToolbarFont()
    }
  }

  private val processLabel = object : JLabel() {
    override fun getFont(): Font {
      return UIUtil.getToolbarFont()
    }
  }.apply {
    foreground = UIUtil.getLabelInfoForeground()
  }

  init {
    layout = MigLayout("novisualpadding, ins 0, gap 0", "[min!][push]")

    add(processLabel)
    add(settingLabel, "wmin 10")

    isOpaque = false
  }

  fun update(process: RunToolbarProcess, environment: List<ExecutionEnvironment>, onlyCount: Boolean = false) {
    this.process = process
    this.environment = environment
    this.onlyCount = onlyCount

    update()
  }

  private fun update() {
    processLabel.text = "${process.shortName}: "
    settingLabel.text = if (environment.size > 1 || onlyCount) {
      environment.size.toString()
    }
    else {
      environment[0].contentToReuse?.displayName ?: ""
    }
  }
}