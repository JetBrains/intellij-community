// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByRegexpReference
import com.intellij.internal.statistic.eventLog.events.RoundedIntEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus

internal object JsonFeatureUsageCollector : CounterUsagesCollector() {
  private val jsonSchemaGroup = EventLogGroup(
    id = "json.schema.features",
    version = 1,
  )

  internal val jsonSchemaHighlightingSessionData =
    jsonSchemaGroup.registerVarargEvent(
      "jsonSchemaHighlightingSession",
      *JsonSchemaFusFeature.getAllRegistered().toTypedArray()
    )

  override fun getGroup(): EventLogGroup = jsonSchemaGroup
}

@ApiStatus.Internal
sealed interface JsonSchemaFusFeature {
  val event: EventField<*>

  companion object {
    fun getAllRegistered(): List<EventField<*>> {
      return listOf(
        JsonSchemaFusRegexpFeature.entries,
        JsonSchemaFusCountedUniqueFeature.entries,
        JsonSchemaFusCountedFeature.entries
      ).flatten()
        .map { it.event }
    }
  }
}

enum class JsonSchemaFusRegexpFeature(override val event: StringEventField) : JsonSchemaFusFeature {
  JsonFusSchemaId(StringValidatedByRegexpReference("schemaId", "^(https?):\\/\\/[^\\s/$.?#].[^\\s]*$"))
}

enum class JsonSchemaFusCountedUniqueFeature(override val event: RoundedIntEventField) : JsonSchemaFusFeature {
  UniqueRemoteUrlDownloadRequest(EventFields.RoundedInt("uniqueRemoteUrlDownloadRequest")),
  SchemaAccessWithoutReadLock(EventFields.RoundedInt("schemaAccessWithoutReadLock")),
}

@ApiStatus.Internal
enum class JsonSchemaFusCountedFeature(override val event: RoundedIntEventField) : JsonSchemaFusFeature {
  ExecutedHttpVirtualFileDownloadRequest(EventFields.RoundedInt("executedHttpVirtualFileDownloadRequest")),
  RemoteUrlResolveRequest(EventFields.RoundedInt("remoteUrlResolveRequest")),
  LocalReferenceResolveRequest(EventFields.RoundedInt("localFileResolveRequest")),
  JsonSchemaResolveTreeBuild(EventFields.RoundedInt("jsonSchemaResolveTreeBuild")),
  AllJsonSchemaResolveTreeBuild(EventFields.RoundedInt("allJsonSchemaResolveTreeBuild")),

  ArrayValidation(EventFields.RoundedInt("arrayValidation")),
  ConstantNodeValidation(EventFields.RoundedInt("constantNodeValidation")),
  EnumValidation(EventFields.RoundedInt("enumValidation")),
  NotValidation(EventFields.RoundedInt("notValidation")),
  StringValidation(EventFields.RoundedInt("stringValidation")),
  NumberValidation(EventFields.RoundedInt("numberValidation")),
  ObjectValidation(EventFields.RoundedInt("objectValidation")),
  TypeValidation(EventFields.RoundedInt("typeValidation")),

  OneOfExpanded(EventFields.RoundedInt("oneOfExpanded")),
  AnyOfExpanded(EventFields.RoundedInt("anyOfExpanded")),
  AllOfExpanded(EventFields.RoundedInt("allOfExpanded")),
  IfElseExpanded(EventFields.RoundedInt("ifElseExpanded")),
  DefinitionsExpanded(EventFields.RoundedInt("definitionsExpanded")),

  SchemaInherited(EventFields.RoundedInt("schemaInherited")),
  SchemaMerged(EventFields.RoundedInt("schemaMerged")),
}