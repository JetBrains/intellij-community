// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target.wizard

import com.intellij.execution.target.*
import com.intellij.ide.IdeBundle
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

internal class WslTargetLanguageStep(model: WslTargetWizardModel) : WslTargetStepBase(model) {
  private lateinit var mainPanel: BorderLayoutPanel
  private var languagesPanel: TargetEnvironmentLanguagesPanel? = null

  override fun _init() {
    super._init()
    stepDescription = getStepDescriptionMessage()
    removeLanguagesPanel()
    val languagesForEditing = model.subject.createMergedLanguagesList(model.languageConfigForIntrospection)
    languagesPanel = TargetEnvironmentLanguagesPanel(model.project, model.subject.getTargetType(),
                                                     { model.subject }, languagesForEditing) {
      forceMainPanelLayout()
    }.also {
      mainPanel.addToCenter(it.component)
      forceMainPanelLayout()
    }
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  private fun getStepDescriptionMessage(): String {
    val displayName = model.languageConfigForIntrospection?.getRuntimeType()?.displayName ?: model.subject.getTargetType().displayName
    return IdeBundle.message("wsl.target.language.step.description", displayName, model.guessName())
  }

  private fun removeLanguagesPanel() {
    languagesPanel?.let {
      mainPanel.remove(it.component)
      it.disposeUIResources()
      languagesPanel = null
    }
  }

  override fun createMainPanel(): JComponent {
    return BorderLayoutPanel().also {
      it.border = JBUI.Borders.empty(LARGE_VGAP, TargetEnvironmentWizard.defaultDialogInsets().right)
      mainPanel = it
    }
  }

  private fun forceMainPanelLayout() {
    with(component) {
      revalidate()
      repaint()
    }
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return languagesPanel?.preferredFocusedComponent
  }

  override fun getStepId(): Any = ID

  override fun getNextStepId(): Any? = null

  override fun getPreviousStepId(): Any = WslTargetIntrospectionStep.ID

  override fun isComplete(): Boolean = true

  override fun doCommit(commitType: CommitType?) {
    if (commitType == CommitType.Finish) {
      languagesPanel?.let {
        it.applyAll()
        model.subject.runtimes.replaceAllWith(it.languagesList.resolvedConfigs())
      }
      model.save()
    }
  }

  companion object {
    @JvmStatic
    val ID: Any = WslTargetLanguageStep::class

    /**
     * Assumes that the first language runtime, if present, is previous introspected version and replaces it with the fresh one.
     * Assumes that all the languages after the first were added by user and leaves them untouched.
     */
    private fun TargetEnvironmentConfiguration.createMergedLanguagesList(introspected: LanguageRuntimeConfiguration?) =
      ContributedConfigurationsList(LanguageRuntimeType.EXTENSION_NAME).also { result ->
        val resolvedConfigs = this.runtimes.resolvedConfigs()
        if (introspected != null) {
          result.addConfig(introspected)
          resolvedConfigs.drop(1)
        }
        resolvedConfigs.forEach { next ->
          result.addConfig(next)
        }
      }
  }
}
