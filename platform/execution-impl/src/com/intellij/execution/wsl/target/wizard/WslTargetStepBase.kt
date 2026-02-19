// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target.wizard

import com.intellij.execution.target.TargetEnvironmentWizardStepKt
import com.intellij.execution.wsl.target.WslTargetType
import com.intellij.util.ui.JBEmptyBorder
import javax.swing.border.Border

internal abstract class WslTargetStepBase(
  val model: WslTargetWizardModel
) : TargetEnvironmentWizardStepKt(WslTargetType.DISPLAY_NAME) {
  companion object {
    /**
     * See https://jetbrains.slack.com/archives/CJULLQS80/p1567071149000100
     * https://jetbrains.slack.com/archives/CJULLQS80/p1587204226005400
     */
    internal fun visualPadding(): Border = JBEmptyBorder(3, 0, 0, 3)
  }
}
