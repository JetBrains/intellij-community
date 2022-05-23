// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.statistic.eventLog

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType.ACCEPTED
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType.REJECTED
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule

class SettingsComponentNameValidator : CustomValidationRule() {
  override fun getRuleId(): String = "component_name"

  override fun acceptRuleId(ruleId: String?): Boolean {
    return getRuleId() == ruleId || "option_name" == ruleId
  }

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    if (isComponentName(data, context)) {
      return if (isComponentNameWhitelisted(data)) ACCEPTED else REJECTED
    }
    return if (isComponentOptionNameWhitelisted(data)) ACCEPTED else REJECTED
  }

  private fun isComponentName(data: String, context: EventContext): Boolean {
    return context.eventData.containsKey("component") && data == context.eventData["component"]
  }
}

class SettingsValueValidator : CustomValidationRule() {
  override fun getRuleId(): String = "setting_value"

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    val componentName = context.eventData["component"] as? String ?: return REJECTED
    val optionName = context.eventData["name"] as? String ?: return REJECTED
    if (!isComponentNameWhitelisted(componentName) || !isComponentOptionNameWhitelisted(optionName)) return REJECTED
    return acceptWhenReportedByJetBrainsPlugin(context)
  }
}