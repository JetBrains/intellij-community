// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo

internal object JsonSchemaCatalogNotificationUsageCollector : CounterUsagesCollector() {
  private val group = EventLogGroup(
    id = "json.schema.catalog.notification",
    version = 1,
  )

  private val schemaCatalogActionEvent = group.registerEvent(
    eventId = "actionInvoked",
    eventField1 = SchemaIdField,
    eventField2 = JsonSchemaCatalogNotificationActionField,
  )

  override fun getGroup(): EventLogGroup = group

  fun logAction(project: Project, schemaInfo: JsonSchemaInfo, action: JsonSchemaCatalogNotificationAction) {
    schemaCatalogActionEvent.log(project, schemaInfo.getUrl(project), action)
  }
}

@Suppress("unused")
internal enum class JsonSchemaCatalogNotificationAction {
  APPLY,
  IGNORE_FILE,
  DISABLE_CATALOG,
}

internal val JsonSchemaCatalogNotificationActionField =
  EventFields.Enum<JsonSchemaCatalogNotificationAction>("action", "Catalog notification action")

internal val SchemaIdField: StringEventField =
  EventFields.StringValidatedByCustomRule("schema_id", JsonSchemaIdValidationRule::class.java, "JSON schema ID")

internal fun logNotificationAction(project: Project, schemaInfo: JsonSchemaInfo, action: JsonSchemaCatalogNotificationAction) {
  JsonSchemaCatalogNotificationUsageCollector.logAction(project, schemaInfo, action)
}
