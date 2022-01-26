// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.codeInsight.hints.settings.InlaySettingsProvider
import com.intellij.codeInsight.hints.settings.language.ParameterInlayProviderSettingsModel
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.lang.Language
import com.intellij.openapi.project.Project

class InlayProviderUsageCollector : ProjectUsagesCollector() {
  private val INLAY_CONFIGURATION_GROUP = EventLogGroup("inlay.configuration", 9)

  private val GLOBAL_SETTINGS_EVENT = INLAY_CONFIGURATION_GROUP.registerEvent(
    "global.inlays.settings",
    EventFields.Boolean("enabled_globally")
  )

  private val LANGUAGE_SETTINGS_EVENT = INLAY_CONFIGURATION_GROUP.registerEvent(
    "language.inlays.settings",
    EventFields.Boolean("enabled"),
    EventFields.Language
  )


  private val MODEL_ID_EVENT_FIELD = object : StringEventField("model") {
    override val validationRule: List<String>
      get() {
        val models = arrayListOf(ParameterInlayProviderSettingsModel.ID)
        models.add("oc.type.hints")
        models.add("tms.local.md.hints")
        InlayHintsProviderExtension.findProviders().mapTo(models) { it.provider.key.id }
        return models
      }
  }


  private val OPTION_ID_EVENT_FIELD = object : StringEventField("option_id") {
    override val validationRule: List<String>
      get() {
        val options = ArrayList<String>()
        val languagesWithSupport = PARAMETER_NAME_HINTS_EP.extensions().map { it.language }
        for (languageId in languagesWithSupport) {
          val language = Language.findLanguageByID(languageId)
          if (language != null) {
            val providers = InlayParameterHintsExtension.allForLanguage(language)
            for (provider in providers) {
              provider.supportedOptions.mapTo(options) { it.id }
            }
          }
        }
        InlayHintsProviderExtension.findProviders().flatMapTo(options) {
          @Suppress("UNCHECKED_CAST") val provider = it.provider as InlayHintsProvider<Any>
          val configurable = provider.createConfigurable(provider.createSettings())
          configurable.cases.map { case -> case.id }
        }
        return options
      }
  }

  private val OPTION_VALUE_EVENT_FIELD = EventFields.Boolean("option_value")
  private val MODEL_ENABLED_EVENT_FIELD = EventFields.Boolean("enabled")

  private val MODEL_SETTINGS_EVENT = INLAY_CONFIGURATION_GROUP.registerEvent(
    "model.inlays.settings",
    EventFields.Boolean("enabled"),
    MODEL_ID_EVENT_FIELD,
    EventFields.Language
  )

  private val MODEL_OPTIONS_EVENT = INLAY_CONFIGURATION_GROUP.registerVarargEvent(
    "model.options",
    MODEL_ID_EVENT_FIELD,
    OPTION_ID_EVENT_FIELD,
    OPTION_VALUE_EVENT_FIELD,
    MODEL_ENABLED_EVENT_FIELD,
    EventFields.Language
  )

  override fun getGroup(): EventLogGroup = INLAY_CONFIGURATION_GROUP

  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    val settingsProviders = InlaySettingsProvider.EP.getExtensions()
    val metricEvents = mutableSetOf<MetricEvent>()
    val settings = InlayHintsSettings.instance()
    metricEvents.add(GLOBAL_SETTINGS_EVENT.metric(settings.hintsEnabledGlobally()))
    for (settingsProvider in settingsProviders) {
      val languages = settingsProvider.getSupportedLanguages(project)
      for (language in languages) {
        val models = settingsProvider.createModels(project, language)
        for (model in models) {
          addModelEvents(model, language, metricEvents)
        }
        metricEvents.add(LANGUAGE_SETTINGS_EVENT.metric(settings.hintsEnabled(language), language))
      }
    }
    return metricEvents
  }

  private fun addModelEvents(model: InlayProviderSettingsModel,
                             language: Language,
                             metrics: MutableSet<MetricEvent>) {
    for (case in model.cases) {
      metrics.add(MODEL_OPTIONS_EVENT.metric(
        EventPair(MODEL_ID_EVENT_FIELD, model.id),
        EventPair(OPTION_ID_EVENT_FIELD, case.id),
        EventPair(OPTION_VALUE_EVENT_FIELD, case.value),
        EventPair(EventFields.Language, language),
        EventPair(MODEL_ENABLED_EVENT_FIELD, model.isEnabled),
      ))
    }
    metrics.add(MODEL_SETTINGS_EVENT.metric(model.isEnabled, model.id, language))
  }
}