// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.impl

import com.intellij.CommonBundle
import com.intellij.codeInspection.options.OptionController
import com.intellij.codeInspection.ui.CustomComponentExtensionWithSwingSelector
import com.intellij.openapi.project.Project
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanQuestion
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import javax.swing.JButton
import javax.swing.JComponent

/**
 * Shows the UI of a [ModPlanQuestion.Custom] for the dialog that [ModPlanQuestion.askCommand] makes for it. The dialog holds only this
 * component, so the IDE calls [select] instead of an options dialog.
 */
@ApiStatus.Internal
class ModPlanQuestionComponent : CustomComponentExtensionWithSwingSelector<Any?>(ModPlanQuestion.Custom.COMPONENT_ID) {
  @RequiresEdt
  override fun select(data: Any?, controller: OptionController, project: Project): Boolean {
    val question = controller.getOption(ModPlanQuestion.Custom.QUESTION_BIND_ID) as ModPlanQuestion.Custom
    val currentValues = controller.getOption(ModPlanQuestion.Custom.ANSWERS_BIND_ID) as ModPlanOptionValues
    val answers = question.ask(project, currentValues) ?: return false
    controller.setOption(ModPlanQuestion.Custom.ANSWERS_BIND_ID, answers)
    return true
  }

  override fun render(data: Any?, controller: OptionController, project: Project): JComponent {
    val button = JButton(CommonBundle.message("button.edit"))
    button.addActionListener { select(data, controller, project) }
    return button
  }
}
