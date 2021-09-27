// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.codeInsight.hints.settings.InlaySettingsProvider
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.lang.Language
import com.intellij.openapi.project.Project

class InlayProviderUsageCollector : ProjectUsagesCollector() {
  private val INLAY_CONFIGURATION_GROUP = EventLogGroup("inlay.configuration", 4)

  private val GLOBAL_SETTINGS_EVENT = INLAY_CONFIGURATION_GROUP.registerEvent(
    "global.inlays.settings",
    EventFields.Boolean("enabled_globally")
  )

  private val LANGUAGE_SETTINGS_EVENT = INLAY_CONFIGURATION_GROUP.registerEvent(
    "language.inlays.settings",
    EventFields.Boolean("enabled"),
    EventFields.Language
  )

  private val MODEL_ID_EVENT_FIELD = EventFields.String("model", allowedValues = listOf(
    "parameter.hints.old",
    "js.chain.hints",
    "js.type.hints",
    "MethodChainsInlayProvider",
    "java.implicit.types",
    "annotation.hints",
    "vcs.code.author",
    "JavaLens",
    "RelatedProblems",
    "microservices.url.path.inlay.hints",
    "groovy.parameters.hints",
    "groovy.variable.type.hints",
    "groovy.implicit.null.argument.hint",
    "docker.inlay.hints",
    "kotlin.references.types.hints",
    "kotlin.lambdas.hints",
    "kotlin.call.chains.hints",
    "kotlin.ranges.hints",
    "composer.package.version.hints",
    "ts.chain.hints",
    "ts.type.hints",
    "ts.enum.hints",
    "sql.join.cardinality.hints",
    "CodeVision",
    "return.values.hints",
    "draft.inlay.hints",
    "docker.inlay.hints",
    "oc.type.hints",
    "tms.local.md.hints"
  ))

  private val OPTION_ID_EVENT_FIELD = EventFields.String("option_id", allowedValues = listOf(
    "angular.show.names.for.all.args",
    "angular.show.names.for.pipes",
    "go.struct.unnamed.struct.fields",
    "go.return.parameters",
    "java.method.name.contains.parameter.name",
    "java.multiple.params.same.type",
    "java.build.like.method",
    "java.simple.sequentially.numbered",
    "java.enums",
    "java.new.expr",
    "java.clear.expression.type",
    "js.only.show.names.for.all.args",
    "js.only.show.names.for.tagged",
    "js.only.show.names.for.pipes",
    "php.show.names.for.all.args",
    "php.pass.by.reference",
    "ruby.non.literals",
    "ruby.method.name.contains.parameter.name",
    "ruby.show.param.grouping",
    "sql.show.column.names.in.insert.values",
    "sql.show.column.names.in.select",
    "sql.show.column.names.for.asterisk",
    "sql.show.column.names.for.set.operations",
    "js.param.hints.show.names.for.all.args",
    "js.param.hints.show.names.for.tagged",
    "vuejs.show.names.for.all.args",
    "vuejs.show.names.for.filters",
    "variables.and.fields",
    "parameters.in.parens",
    "non.paren.single.param",
    "function.returns",
    "inferred.annotations",
    "external.annotations",
    "usages",
    "inheritors",
    "inferred.parameter.types",
    "type.parameter.list",
    "hints.type.property",
    "hints.type.variable",
    "hints.type.function.return",
    "hints.type.function.parameter",
    "hints.lambda.return",
    "hints.lambda.receivers.parameters",
    "inner.join",
    "left.join",
    "right.join",
    "full.join"
  ))

  private val OPTION_VALUE_EVENT_FIELD = EventFields.Boolean("option_value")
  private val MODEL_ENABLED_EVENT_FIELD = EventFields.Boolean("enabled")

  private val MODEL_SETTINGS_EVENT = INLAY_CONFIGURATION_GROUP.registerEvent(
    "model.inlays.settings",
    EventFields.Boolean("enabled"),
    MODEL_ID_EVENT_FIELD
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
    metrics.add(MODEL_SETTINGS_EVENT.metric(model.isEnabled, model.id))
  }
}