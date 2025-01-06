// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.utils

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.PluginInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MicroservicesUsageCollector : CounterUsagesCollector() {
  private val MICROSERVICES_USAGES_GROUP: EventLogGroup = EventLogGroup("microservices.usages", 11)

  override fun getGroup(): EventLogGroup = MICROSERVICES_USAGES_GROUP

  val URL_PATH_SEGMENT_NAVIGATE_EVENT: EventId = MICROSERVICES_USAGES_GROUP.registerEvent("url.path.segment.navigate")
  val URL_PATH_VARIANTS_EVENT: EventId = MICROSERVICES_USAGES_GROUP.registerEvent("url.path.reference.variants")
  val URL_INLAY_ACTIONS_EVENT: EventId = MICROSERVICES_USAGES_GROUP.registerEvent("url.path.inlay.actions")

  const val URL_INLAY_FIND_USAGES_ACTION_ID: String = "find_usages"
  const val URL_INLAY_OPEN_ENDPOINTS_ACTION_ID: String = "open_endpoints"
  const val URL_INLAY_GENERATE_REQUEST_ACTION_ID: String = "generate_request"
  const val URL_INLAY_GENERATE_OPENAPI_ACTION_ID: String = "generate_openapi"
  const val URL_INLAY_GENERATE_TEST_ACTION_ID: String = "generate_test"
  const val URL_INLAY_SECURED_URLS_ACTION_ID: String = "show_secured_urls"
  const val URL_INLAY_SECURITY_MATCHERS_ACTION_ID: String = "show_security_matchers"

  val URL_INLAY_ACTION_TRIGGERED_EVENT: EventId1<String> =
    MICROSERVICES_USAGES_GROUP.registerEvent("url.path.inlay.action.triggered",
                                             StringEventField.ValidatedByAllowedValues("action_id", listOf(
                                               URL_INLAY_FIND_USAGES_ACTION_ID,
                                               URL_INLAY_OPEN_ENDPOINTS_ACTION_ID,
                                               URL_INLAY_GENERATE_REQUEST_ACTION_ID,
                                               URL_INLAY_GENERATE_OPENAPI_ACTION_ID,
                                               URL_INLAY_GENERATE_TEST_ACTION_ID,
                                               URL_INLAY_SECURED_URLS_ACTION_ID,
                                               URL_INLAY_SECURITY_MATCHERS_ACTION_ID
                                             )))

  val MQ_ID_NAVIGATE_EVENT: EventId = MICROSERVICES_USAGES_GROUP.registerEvent("mq.reference.navigate")
  val MQ_ID_VARIANTS_EVENT: EventId = MICROSERVICES_USAGES_GROUP.registerEvent("mq.reference.variants")

  val ENDPOINTS_REQUESTED_EVENT: EventId2<String?, PluginInfo?> =
    MICROSERVICES_USAGES_GROUP.registerEvent("endpoints.groups.requested",
                                             EventFields.StringValidatedByCustomRule("endpoints_provider",
                                                                                     EndpointsProviderNameRule::class.java),
                                             EventFields.PluginInfo)

  val ENDPOINTS_NAVIGATED_EVENT: EventId1<PluginInfo?> =
    MICROSERVICES_USAGES_GROUP.registerEvent("endpoints.navigated", EventFields.PluginInfo)

  const val FRAMEWORK_FILTER_ID: String = "framework"
  const val MODULE_FILTER_ID: String = "module"
  const val TYPE_FILTER_ID: String = "type"

  val ENDPOINTS_FILTERED_EVENT: EventId1<List<String>> =
    MICROSERVICES_USAGES_GROUP.registerEvent("endpoints.list.filtered",
                                             StringListEventField.ValidatedByAllowedValues("filter_id", listOf(
                                               FRAMEWORK_FILTER_ID, MODULE_FILTER_ID, TYPE_FILTER_ID
                                             )))

  // Activated = updated when selected + explicitly selected
  val ENDPOINTS_HTTP_CLIENT_ACTIVATED_EVENT: EventId = MICROSERVICES_USAGES_GROUP.registerEvent("endpoints.tab.http.client.activated")
  val ENDPOINTS_OPENAPI_ACTIVATED_EVENT: EventId = MICROSERVICES_USAGES_GROUP.registerEvent("endpoints.tab.openapi.activated")
  val ENDPOINTS_EXAMPLES_ACTIVATED_EVENT: EventId = MICROSERVICES_USAGES_GROUP.registerEvent("endpoints.tab.examples.activated")
  val ENDPOINTS_DOCUMENTATION_ACTIVATED_EVENT: EventId = MICROSERVICES_USAGES_GROUP.registerEvent("endpoints.tab.documentation.activated")

  val ENDPOINTS_EMPTY_STATE_ACTIVATED_EVENT: EventId = MICROSERVICES_USAGES_GROUP.registerEvent("endpoints.empty.state.activated")
}