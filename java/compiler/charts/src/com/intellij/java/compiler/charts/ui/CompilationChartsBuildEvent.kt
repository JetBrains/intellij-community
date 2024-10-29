// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.build.BuildViewManager
import com.intellij.build.events.BuildEventPresentationData
import com.intellij.build.events.PresentableBuildEvent
import com.intellij.build.events.impl.AbstractBuildEvent
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.icons.AllIcons
import com.intellij.java.compiler.charts.CompilationChartsBundle
import com.intellij.java.compiler.charts.CompilationChartsViewModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createLifetime
import javax.swing.Icon
import javax.swing.JComponent

class CompilationChartsBuildEvent(project: Project, val view: BuildViewManager, val buildId: Any, disposable: Disposable) :
  AbstractBuildEvent(Any(), buildId, System.currentTimeMillis(), CompilationChartsBundle.message("charts.tab.name")),
  PresentableBuildEvent {

  private val console: CompilationChartsExecutionConsole by lazy { CompilationChartsExecutionConsole(project, disposable) }

  override fun getPresentationData(): BuildEventPresentationData = CompilationChartsPresentationData(console)

  fun vm(): CompilationChartsViewModel = console.vm

  private class CompilationChartsPresentationData(private val component: ExecutionConsole) : BuildEventPresentationData {
    override fun getNodeIcon(): Icon = AllIcons.Actions.Profile

    override fun getExecutionConsole(): ExecutionConsole = component

    override fun consoleToolbarActions(): ActionGroup? = null
  }

  private class CompilationChartsExecutionConsole(project: Project, disposable: Disposable) : ExecutionConsole {
    val vm: CompilationChartsViewModel = CompilationChartsViewModel(this.createLifetime(), disposable)
    private val _component: CompilationChartsView by lazy {
      CompilationChartsView(project, vm)
    }

    override fun dispose() {
    }

    override fun getComponent(): JComponent = _component
    override fun getPreferredFocusableComponent(): JComponent = _component
  }
}
