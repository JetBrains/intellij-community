// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.*
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.Nls
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import javax.swing.Icon
import javax.swing.JCheckBox

private val ID: Key<LaunchBrowserBeforeRunTask> = Key.create("LaunchBrowser.Before.Run")

internal class LaunchBrowserBeforeRunTaskProvider : BeforeRunTaskProvider<LaunchBrowserBeforeRunTask>(), DumbAware {

  override fun getName(): @Nls String = IdeBundle.message("task.browser.launch")

  override fun getId(): Key<LaunchBrowserBeforeRunTask> = ID

  override fun getIcon(): Icon = AllIcons.Nodes.PpWeb

  override fun isConfigurable(): Boolean = true

  override fun createTask(runConfiguration: RunConfiguration): LaunchBrowserBeforeRunTask = LaunchBrowserBeforeRunTask()

  override fun configureTask(context: DataContext, runConfiguration: RunConfiguration, task: LaunchBrowserBeforeRunTask): Promise<Boolean> {
    val state = task.state
    val modificationCount = state.modificationCount

    val browserSelector = BrowserSelector()
    val browserComboBox = browserSelector.mainComponent
    state.browser?.let {
      browserSelector.selected = it
    }

    val url = TextFieldWithBrowseButton()
    state.url?.let {
      url.text = it
    }

    StartBrowserPanel.setupUrlField(url, runConfiguration.project)

    var startJavaScriptDebuggerCheckBox: JCheckBox? = null

    val panel = panel {
      row(IdeBundle.message("task.browser.label")) {
        cell(browserComboBox)
          .resizableColumn()
          .align(AlignX.FILL)
        if (JavaScriptDebuggerStarter.Util.hasStarters()) {
          startJavaScriptDebuggerCheckBox = checkBox(IdeBundle.message("start.browser.with.js.debugger"))
            .selected(state.withDebugger)
            .component
        }
      }
      row(IdeBundle.message("task.browser.url")) {
        cell(url)
          .align(AlignX.FILL)
          .columns(COLUMNS_MEDIUM)
      }
    }
    dialog(IdeBundle.message("task.browser.launch"), panel = panel, resizable = true, focusedComponent = url)
      .show()

    state.browser = browserSelector.selected
    state.url = url.text
    startJavaScriptDebuggerCheckBox?.let {
      state.withDebugger = it.isSelected
    }
    return resolvedPromise(modificationCount != state.modificationCount)
  }

  override fun executeTask(context: DataContext,
                           configuration: RunConfiguration,
                           env: ExecutionEnvironment,
                           task: LaunchBrowserBeforeRunTask): Boolean {
    val disposable = Disposer.newDisposable()
    Disposer.register(env.project, disposable)
    val executionId = env.executionId
    env.project.messageBus.connect(disposable).subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
      override fun processNotStarted(executorId: String, env: ExecutionEnvironment) {
        Disposer.dispose(disposable)
      }

      override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        if (env.executionId != executionId) {
          return
        }

        Disposer.dispose(disposable)

        val settings = StartBrowserSettings()
        settings.browser = task.state.browser
        settings.isStartJavaScriptDebugger = task.state.withDebugger
        settings.url = task.state.url
        settings.isSelected = true
        BrowserStarter(configuration, settings, handler).start()
      }
    })
    return true
  }
}

internal class LaunchBrowserBeforeRunTaskState : BaseState() {
  @get:Attribute(value = "browser", converter = WebBrowserReferenceConverter::class)
  var browser: WebBrowser? by property(null) { it == null }

  @get:Attribute()
  var url: String? by string()

  @get:Attribute()
  var withDebugger: Boolean by property(false)
}

internal class LaunchBrowserBeforeRunTask : BeforeRunTask<LaunchBrowserBeforeRunTask>(ID),
                                            PersistentStateComponent<LaunchBrowserBeforeRunTaskState> {
  private var state = LaunchBrowserBeforeRunTaskState()

  override fun loadState(state: LaunchBrowserBeforeRunTaskState) {
    state.resetModificationCount()
    this.state = state
  }

  override fun getState(): LaunchBrowserBeforeRunTaskState = state
}