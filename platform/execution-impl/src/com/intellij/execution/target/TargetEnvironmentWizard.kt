// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.ExecutionBundle
import com.intellij.ide.wizard.AbstractWizardEx
import com.intellij.ide.wizard.AbstractWizardStepEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.Border

class TargetEnvironmentWizard(project: Project,
                              @NlsContexts.DialogTitle title: String,
                              val subject: TargetEnvironmentConfiguration,
                              steps: List<AbstractWizardStepEx>)
  : AbstractWizardEx(title, project, steps) {

  init {
    for (step in steps) {
      if (step is ValidationCallbackConsumer) {
        step.accept { setErrorInfoAll(step.doValidateAll()) }
      }
    }
  }

  override fun getHelpId(): String = "reference.remote.target.wizard.${subject.typeId}"

  override fun createContentPaneBorder(): Border = JBUI.Borders.empty()

  override fun createSouthPanel(): JComponent =
    JPanel(BorderLayout()).also {
      it.add(super.createSouthPanel(), BorderLayout.CENTER)
      it.border = JBUI.Borders.empty(0, 12, 8, 12)
    }

  companion object {
    @JvmStatic
    fun <C : TargetEnvironmentConfiguration> createWizard(
      project: Project, targetType: TargetEnvironmentType<C>, runtimeType: LanguageRuntimeType<*>?
    ): TargetEnvironmentWizard? {

      if (!targetType.providesNewWizard(project, runtimeType)) {
        return null
      }

      val config = targetType.createDefaultConfig()
      val steps = targetType.createStepsForNewWizard(project, config, runtimeType) ?: return null

      return TargetEnvironmentWizard(
        project, ExecutionBundle.message("run.on.targets.wizard.title.new.target"), config, steps)
    }

    /**
     * To compensate empty TargetEnvironmentWizard.createContentPaneBorder()
     */
    fun defaultDialogInsets() = UIUtil.getRegularPanelInsets()
  }

  interface ValidationCallbackConsumer : Consumer<() -> Unit> {
    fun doValidateAll(): MutableList<ValidationInfo>
  }
}