// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.RoundedIntEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object JsonFeatureUsageCollector : CounterUsagesCollector() {
  private val jsonSchemaGroup = EventLogGroup(
    id = "json.schema.features",
    version = 5,
  )

  internal val jsonSchemaHighlightingSessionData =
    jsonSchemaGroup.registerVarargEvent(
      "json.schema.highlighting.session.finished",
      *JsonSchemaFusFeature.getAllRegistered().toTypedArray()
    )

  override fun getGroup(): EventLogGroup = jsonSchemaGroup
}

internal sealed interface JsonSchemaFusFeature {
  val event: EventField<*>

  companion object {
    fun getAllRegistered(): List<EventField<*>> {
      return listOf(
        JsonSchemaFusAllowedListFeature.entries,
        JsonSchemaFusCountedUniqueFeature.entries,
        JsonSchemaFusCountedFeature.entries
      ).flatten()
        .map { it.event }
    }
  }
}

internal enum class JsonSchemaFusAllowedListFeature(override val event: StringEventField) : JsonSchemaFusFeature {
  JsonFusSchemaId(EventFields.StringValidatedByCustomRule("schema_id", JsonSchemaIdValidationRule::class.java, "JSON schema ID"))
}

internal enum class JsonSchemaFusCountedUniqueFeature(override val event: RoundedIntEventField) : JsonSchemaFusFeature {
  UniqueRemoteUrlDownloadRequest(EventFields.RoundedInt("unique_remote_url_download_request", "Number of unique remote (URL) references collected during highlighting session")),
}

internal enum class JsonSchemaFusCountedFeature(override val event: RoundedIntEventField) : JsonSchemaFusFeature {
  ExecutedHttpVirtualFileDownloadRequest(EventFields.RoundedInt("executed_http_virtual_file_download_request", "Remote (URL) reference download was called)")),
  RemoteUrlResolveRequest(EventFields.RoundedInt("remote_url_resolve_request", "Remote (URL) reference resolve was called")),
  LocalReferenceResolveRequest(EventFields.RoundedInt("local_file_resolve_request", "Local reference resolve was called")),

  JsonSchemaResolveTreeBuild(EventFields.RoundedInt("json_schema_resolve_tree_build", "DetailedResolve was called")),

  ArrayValidation(EventFields.RoundedInt("array_validation", "Array validator was called")),
  ConstantNodeValidation(EventFields.RoundedInt("constant_node_validation", "Constant validator was called")),
  EnumValidation(EventFields.RoundedInt("enum_validation", "Enum validator was called")),
  NotValidation(EventFields.RoundedInt("not_validation", "Not validator was called")),
  StringValidation(EventFields.RoundedInt("string_validation", "String validator was called")),
  NumberValidation(EventFields.RoundedInt("number_validation", "Number validator was called")),
  ObjectValidation(EventFields.RoundedInt("object_validation", "Object validator was called")),
  TypeValidation(EventFields.RoundedInt("type_validation", "Type validator was called")),

  OneOfExpanded(EventFields.RoundedInt("one_of_expanded", "OneOf schema node was expanded")),
  AnyOfExpanded(EventFields.RoundedInt("any_of_expanded", "AnyOf schema node was expanded")),
  AllOfExpanded(EventFields.RoundedInt("all_of_expanded", "AllOf schema node was expanded")),
  IfElseExpanded(EventFields.RoundedInt("if_else_expanded", "IfElse schema node was expanded")),
  DefinitionsExpanded(EventFields.RoundedInt("definitions_expanded", "Definition reference was expanded")),

  SchemaInherited(EventFields.RoundedInt("schema_inherited", "Schema node was inherited")),
  SchemaMerged(EventFields.RoundedInt("schema_merged", "Schema node was merged")),
}