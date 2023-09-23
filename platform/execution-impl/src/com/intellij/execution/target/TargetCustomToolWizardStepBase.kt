// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.ExecutionExceptionWithAttachments
import com.intellij.ide.wizard.CommitStepException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JComponent
import kotlin.jvm.Throws

abstract class TargetCustomToolWizardStepBase<M : TargetWizardModel>(@NlsContexts.DialogTitle title: String,
                                                                     protected val model: M)
  : TargetEnvironmentWizardStepKt(title), TargetCustomToolWizardStep {
  private lateinit var mainPanel: BorderLayoutPanel

  private var customToolPanel: TargetCustomToolPanel? = null

  final override var customTool: Any? = null
    private set

  /**
   * The configuration that is used during creating or editing this custom tool. It might differ from [TargetWizardModel.subject] field
   * because the subject and the configuration entities it depends upon might not yet be saved at this point in IDE they might be unsafe to
   * use.
   */
  protected open val editingTargetConfiguration: TargetEnvironmentConfiguration
    get() = model.subject

  final override fun getPreferredFocusedComponent(): JComponent? = customToolPanel?.preferredFocusedComponent

  final override fun getNextStepId(): Any? = null

  final override fun isComplete(): Boolean = true

  final override fun _init() {
    super._init()

    // as _init() method is called every time the step is shown we recreate the content based on the new target data
    removeCustomToolPanel()

    stepDescription = getInitStepDescription()

    model.applyChanges()

    // TODO [targets] get rid of `!!` in `model.languageConfigForIntrospection!!`
    customToolPanel = TargetCustomToolPanel(model.project, model.subject.getTargetType(), ::editingTargetConfiguration,
                                            model.languageConfigForIntrospection!!, createIntrospectable())
      .also {
        mainPanel.addToCenter(it.component)
      }
    // triggers resizing of the wizard dialog according to the newly created panel
    forceMainPanelLayout()
  }

  private fun removeCustomToolPanel() {
    customToolPanel?.let {
      mainPanel.remove(it.component)
      it.disposeUIResources()
      customToolPanel = null
    }
  }

  protected abstract fun getInitStepDescription(): String

  protected open fun createIntrospectable(): LanguageRuntimeType.Introspectable? = null

  private fun forceMainPanelLayout() {
    with(component) {
      revalidate()
      repaint()
    }
  }

  final override fun createMainPanel(): JComponent {
    return BorderLayoutPanel().also {
      mainPanel = it
      mainPanel.border = JBUI.Borders.empty(LARGE_VGAP, TargetEnvironmentWizard.defaultDialogInsets().right)
    }
  }

  @Throws(CommitStepException::class)
  override fun doCommit(commitType: CommitType?) {
    if (commitType == CommitType.Finish) {
      val customToolPanel = customToolPanel!!
      customToolPanel.validateCustomTool().firstOrNull()?.let { throw CommitStepException(it.message) }
      customToolPanel.let {
        it.applyAll()
        model.subject.runtimes.replaceAllWith(listOf(model.languageConfigForIntrospection!!))
      }

      try {
        model.applyChanges()
        customTool = customToolPanel.createCustomTool()
        model.commit()
      }
      catch (err: ExecutionExceptionWithAttachments) {
        LOG.error("Failed to create a target", err)
        val message = err.message?.let { "$it\n\n" } ?: ""
        val moreInfo = "${message}${err.stderr}\n\n${err.stdout}".trim { it <= ' ' }
        throw CommitStepException(moreInfo)
      }
    }
  }

  companion object {
    @JvmStatic
    val ID: Any = TargetCustomToolWizardStepBase::class

    private val LOG = logger<TargetCustomToolWizardStepBase<*>>()
  }
}