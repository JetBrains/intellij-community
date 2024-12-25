// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.utils

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule

internal class EndpointsProviderNameRule : CustomValidationRule() {
  override fun getRuleId(): String = "endpoint_provider_name"

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    if (isThirdPartyValue(context.eventData["endpoints_provider"].toString())) return ValidationResultType.ACCEPTED

    return acceptWhenReportedByJetBrainsPlugin(context)
  }
}