// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target.wizard

import com.intellij.execution.process.ConsoleHighlighter
import com.intellij.execution.target.LanguageRuntimeConfiguration
import com.intellij.execution.target.TargetEnvironmentWizard
import com.intellij.execution.target.getRuntimeType
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.target.WslTargetConfigurable
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.TerminalExecutionConsole
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.UserActivityWatcher
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.NotNull
import javax.swing.JComponent
import javax.swing.border.Border

internal class WslTargetIntrospectionStep(model: WslTargetWizardModel) : WslTargetStepBase(model) {

  private val selectDistributionConfigurable: WslTargetConfigurable by lazy {
    WslTargetConfigurable(WslTargetEnvironmentConfiguration(null), model.project)
  }
  private val console: ConsoleView by lazy {
    TerminalExecutionConsole(model.project, 86, 18, null).also {
      it.terminalWidget.terminalPanel.setCursorVisible(false)
      it.terminalWidget.terminalPanel.isFocusable = false
      it.withConvertLfToCrlfForNonPtyProcess(true)
      Disposer.register(this, it)
    }
  }
  private var introspectionFinished = false

  override fun _init() {
    super._init()
    model.resetLanguageConfigForIntrospection()
    stepDescription = IdeBundle.message("wsl.target.introspection.step.description")
    console.clear()
    selectDistributionConfigurable.config.distribution = model.distribution
    model.distribution = null
    selectDistributionConfigurable.reset()
    tryIntrospecting()
  }

  private fun tryIntrospecting() {
    selectDistributionConfigurable.apply()
    selectDistributionConfigurable.config.distribution?.let {
      if (it != model.distribution) {
        model.distribution = it
        introspect(it)
      }
    }
  }

  private fun introspect(distribution: WSLDistribution) {
    setSpinningVisible(true)
    model.resetLanguageConfigForIntrospection()
    console.clear()
    val modalityState = ModalityState.stateForComponent(console.component)
    introspectionFinished = false
    ApplicationManager.getApplication().executeOnPooledThread {
      val introspectable = WslTargetIntrospectable(distribution, console)
      introspectable.promiseExecuteScript("pwd").thenApply {
        if (it != null) {
          model.subject.projectRootOnTarget = "${it}/${model.project.name}"
        }
      }
      introspect(model.languageConfigForIntrospection, introspectable, modalityState)
    }
  }

  private fun introspect(languageRuntimeConfiguration: LanguageRuntimeConfiguration?,
                         introspectable: WslTargetIntrospectable,
                         modalityState: @NotNull ModalityState) {
    if (languageRuntimeConfiguration == null) {
      finalizeIntrospection(modalityState)
      return
    }
    val introspector = languageRuntimeConfiguration.getRuntimeType().createIntrospector(languageRuntimeConfiguration)
    if (introspector == null) {
      finalizeIntrospection(modalityState)
      return
    }

    introspector.introspect(introspectable)
      .thenApply { model.languageConfigForIntrospection }
      .whenComplete { _: Any?, err: Throwable? ->
        if (err == null) {
          console.print(IdeBundle.message("wsl.target.introspection.step.completed.successfully"),
                        ConsoleViewContentType("green", ConsoleHighlighter.GREEN))
        }
        else {
          console.print(IdeBundle.message("wsl.target.introspection.step.completed.with.errors"),
                        ConsoleViewContentType.ERROR_OUTPUT)
        }
        introspectable.shutdown()
        finalizeIntrospection(modalityState)
      }
  }

  private fun finalizeIntrospection(modalityState: @NotNull ModalityState) {
    ApplicationManager.getApplication().invokeLater({
                                                      introspectionFinished = true
                                                      setSpinningVisible(false)
                                                      fireStateChanged()
                                                    }, modalityState)
  }

  override fun createMainPanel(): JComponent {
    val panel = BorderLayoutPanel()
    val selectDistributionComponent = selectDistributionConfigurable.createComponent()!!
    selectDistributionComponent.border = createBorder()
    panel.addToTop(selectDistributionComponent)
    panel.addToCenter(console.component)

    val watcher = UserActivityWatcher()
    watcher.register(selectDistributionComponent)
    watcher.addUserActivityListener {
      tryIntrospecting()
    }
    return panel
  }

  private fun createBorder(): @NotNull Border {
    return JBUI.Borders.merge(
      JBUI.Borders.empty(LARGE_VGAP, TargetEnvironmentWizard.defaultDialogInsets().left, 0, 0),
      JBUI.Borders.merge(visualPadding(), IdeBorderFactory.createBorder(SideBorder.BOTTOM), true),
      true)
  }

  override fun getPreferredFocusedComponent(): JComponent {
    val preferredFocusedComponent = selectDistributionConfigurable.preferredFocusedComponent
    return preferredFocusedComponent ?: console.preferredFocusableComponent
  }

  override fun getStepId(): Any = ID

  override fun getNextStepId(): Any = if (model.isCustomToolConfiguration) WslTargetCustomToolStep.ID else WslTargetLanguageStep.ID

  override fun getPreviousStepId(): Any? = null

  override fun isComplete(): Boolean = introspectionFinished && model.distribution != null

  override fun doCommit(commitType: CommitType?) {
  }

  override fun dispose() {
    selectDistributionConfigurable.disposeUIResources()
    super.dispose()
  }

  companion object {
    @JvmStatic
    val ID: Any = WslTargetIntrospectionStep::class
  }
}
