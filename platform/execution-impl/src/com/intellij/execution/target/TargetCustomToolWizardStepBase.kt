// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ExecutionExceptionWithAttachments
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JComponent

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

    model.save()

    // TODO [targets] get rid of `!!` in `model.languageConfigForIntrospection!!`
    customToolPanel = TargetCustomToolPanel(model.project, model.subject.getTargetType(), ::editingTargetConfiguration,
                                            model.languageConfigForIntrospection!!)
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

  override fun doCommit(commitType: CommitType?) {
    if (commitType == CommitType.Finish) {
      customToolPanel!!.let {
        it.applyAll()
        model.subject.runtimes.replaceAllWith(listOf(model.languageConfigForIntrospection!!))
      }

      try {
        model.commit()

        customTool = customToolPanel!!.createCustomTool(model.subject)
      }
      catch (err: Throwable) {
        LOG.error("Failed to create a target", err)
        showErrorDialog(model.project, err)
      }
    }
  }

  companion object {
    @JvmStatic
    val ID: Any = TargetCustomToolWizardStepBase::class

    private val LOG = logger<TargetCustomToolWizardStepBase<*>>()

    private fun showErrorDialog(
      project: Project?,
      err: Throwable
    ) {
      val title = ExecutionBundle.message("run.on.targets.wizard.creation.error.dialog.title")
      val message = err.localizedMessage ?: err.message ?: ""
      if (err is ExecutionExceptionWithAttachments) {
        val moreInfo = "${err.stderr}\n\n${err.stdout}".trim { it <= ' ' }
        if (moreInfo.isNotEmpty()) {
          Messages.showDialog(
            project,
            message,
            title,
            moreInfo, arrayOf(Messages.getOkButton()),
            0,
            0,
            Messages.getErrorIcon()
          )
          return
        }
      }
      Messages.showErrorDialog(message, title)
    }
  }
}