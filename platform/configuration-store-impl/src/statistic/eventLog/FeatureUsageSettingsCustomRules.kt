// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.statistic.eventLog

import com.intellij.internal.statistic.eventLog.validator.rules.beans.EventGroupContextData
import com.intellij.internal.statistic.eventLog.validator.rules.impl.ComposerValidationRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRuleFactory
import com.jetbrains.fus.reporting.api.IEventContext
import com.jetbrains.fus.reporting.api.ValidationResultType

internal class SettingsValueValidatorFactory : CustomValidationRuleFactory {
  override fun createValidator(contextData: EventGroupContextData): SettingsValueValidator = SettingsValueValidator(contextData)

  override fun getRuleId(): String = SettingsValueValidator.RULE_ID

  override fun getRuleClass(): Class<*> = SettingsValueValidator::class.java
}

internal class SettingsComponentNameValidator : CustomValidationRule() {
  override fun getRuleId(): String = "component_name"

  override fun acceptRuleId(ruleId: String?): Boolean = getRuleId() == ruleId || "option_name" == ruleId

  override fun doValidate(data: String, context: IEventContext): ValidationResultType {
    if (isComponentName(data, context)) {
      return if (isComponentNameWhitelisted(data)) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
    } else {
      return if (isComponentOptionNameWhitelisted(data)) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
    }
  }

  private fun isComponentName(data: String, context: IEventContext): Boolean =
    context.eventData.containsKey("component") && data == context.eventData["component"]
}

internal class SettingsValueValidator(contextData: EventGroupContextData) : CustomValidationRule() {
  private val composerValidationRule = ComposerValidationRule(listOf(
    contextData.getEnumValidationRule("boolean"),
    contextData.getRegexpValidationRule("integer"),
    contextData.getRegexpValidationRule("float"))
  )

  companion object {
    const val RULE_ID = "setting_value"
  }

  override fun getRuleId(): String = RULE_ID

  override fun doValidate(data: String, context: IEventContext): ValidationResultType {
    if  (composerValidationRule.doValidate(data, context) == ValidationResultType.ACCEPTED) return ValidationResultType.ACCEPTED
    val componentName = context.eventData["component"] as? String ?: return ValidationResultType.REJECTED
    val optionName = context.eventData["name"] as? String ?: return ValidationResultType.REJECTED
    if (!isComponentNameWhitelisted(componentName) || !isComponentOptionNameWhitelisted(optionName)) return ValidationResultType.REJECTED
    return acceptWhenReportedByJetBrainsPlugin(context)
  }
}
