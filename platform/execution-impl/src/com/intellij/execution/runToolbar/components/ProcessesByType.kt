// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar.components

import com.intellij.execution.runToolbar.RunToolbarProcess
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JPanel

@ApiStatus.Internal
class ProcessesByType : JPanel() {

  private var onlyCount = true
  private var environment = emptyList<ExecutionEnvironment>()
  private var process = RunToolbarProcess.getProcesses()[0]


  private val settingLabel = object : TrimmedMiddleLabel() {
    override fun getFont(): Font {
      return UIUtil.getToolbarFont()
    }

    override fun getForeground(): Color {
      return UIUtil.getLabelForeground()
    }
  }

  private val processLabel = object : JLabel() {
    override fun getFont(): Font {
      return UIUtil.getToolbarFont()
    }

    override fun getForeground(): Color {
      return UIUtil.getLabelInfoForeground()
    }
  }

  init {
    layout = MigLayout("fill, novisualpadding, ins 0, gap 0", "[min!][fill]")

    add(processLabel)
    add(settingLabel, "wmin 10")

    isOpaque = false
  }

  fun update(process: RunToolbarProcess, environment: MutableList<ExecutionEnvironment>, onlyCount: Boolean = false) {
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