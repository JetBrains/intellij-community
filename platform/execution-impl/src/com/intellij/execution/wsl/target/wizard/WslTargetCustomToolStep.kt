// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target.wizard

import com.intellij.execution.target.TargetCustomToolWizardStepBase
import com.intellij.execution.wsl.target.WslTargetType
import com.intellij.ide.IdeBundle

class WslTargetCustomToolStep(model: WslTargetWizardModel)
  : TargetCustomToolWizardStepBase<WslTargetWizardModel>(WslTargetType.DISPLAY_NAME, model) {
  override fun getInitStepDescription(): String = formatStepLabel(3, 3, IdeBundle.message("wsl.target.tool.step.description"))

  override fun getStepId(): Any = ID

  override fun getPreviousStepId(): Any = WslTargetIntrospectionStep.ID

  companion object {
    @JvmStatic
    internal val ID = WslTargetCustomToolStep::class
  }
}