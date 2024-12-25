package com.intellij.microservices.utils.stats

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