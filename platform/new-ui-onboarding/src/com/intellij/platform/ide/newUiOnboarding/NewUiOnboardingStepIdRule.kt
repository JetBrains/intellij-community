// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.utils.getPluginInfo

internal class NewUiOnboardingStepIdRule : CustomValidationRule() {
  override fun getRuleId(): String = "newUiOnboardingStepId"

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val step = NewUiOnboardingStep.EP_NAME.findFirstSafe { it.key == data }
    if (step == null) {
      return ValidationResultType.REJECTED
    }
    val isDevelopedByJB = getPluginInfo(step.implementationClass).isDevelopedByJetBrains()
    return if (isDevelopedByJB) ValidationResultType.ACCEPTED else ValidationResultType.THIRD_PARTY
  }
}