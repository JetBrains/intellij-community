// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.help.impl

import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType
import com.jetbrains.fus.reporting.api.ValidationResultType.ACCEPTED
import com.jetbrains.fus.reporting.api.ValidationResultType.REJECTED
import com.jetbrains.fus.reporting.api.ValidationResultType.THIRD_PARTY

internal class HelpTopicCustomValidationRule : CustomValidationRule() {
  override fun getRuleId(): String = "help_topic"

  override fun doValidate(data: String, context: IEventContext): ValidationResultType {
    HelpManagerImpl.findProviderFor(data)?.let { resolvedProvider ->
      return if (resolvedProvider.isJetBrainsProvider) ACCEPTED else THIRD_PARTY
    }
    return if (HelpManagerImpl.getHelpUrl(data) != null) ACCEPTED else REJECTED
  }
}