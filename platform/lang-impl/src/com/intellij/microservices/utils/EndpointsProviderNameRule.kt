// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.utils

import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType

internal class EndpointsProviderNameRule : CustomValidationRule() {
  override fun getRuleId(): String = "endpoint_provider_name"

  override fun doValidate(data: String, context: IEventContext): ValidationResultType =
    if (isThirdPartyValue(context.eventData["endpoints_provider"].toString())) ValidationResultType.ACCEPTED
    else acceptWhenReportedByJetBrainsPlugin(context)
}
