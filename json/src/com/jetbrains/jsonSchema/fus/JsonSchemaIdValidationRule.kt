// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.fus

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.openapi.diagnostic.thisLogger
import java.io.IOException

/**
 * All known schema ids and names downloaded from https://schemastore.org/api/json/catalog.json
 * The list is quite big, so it is extracted to a separate resource file and must be loaded outside EDT to prevent possible freezes.
 */
internal class JsonSchemaIdValidationRule : CustomValidationRule() {
  override fun getRuleId(): String = "json_schema_id_rule"

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    return if (AllowListHolder.allowedNames.contains(data)) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
  }

  object AllowListHolder {
    val allowedNames: Set<String> by lazy {
      deserialiseBundledAllowedSchemaIds()
    }

    fun deserialiseBundledAllowedSchemaIds(): Set<String> {
      val bundledDataStream =
        try {
          JsonSchemaIdValidationRule::class.java.getResourceAsStream("KnownSchemaIdentifiers.json").use { stream ->
            val objectMapper = ObjectMapper(
              JsonFactory.builder().enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build()
            )
            objectMapper.readValue<ArrayList<KnownJsonSchemaIdentity>>(
              stream,
              objectMapper.typeFactory.constructCollectionType(ArrayList::class.java, KnownJsonSchemaIdentity::class.java)
            )
          }
        }
        catch (exception: IOException) {
          thisLogger().warn("Failed to load bundled allowed schema identifiers", exception)
          return emptySet()
        }
      return bundledDataStream
        .asSequence()
        .flatMap { sequenceOf(it.url, it.fileName).filterNotNull() }
        .toSet()
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class KnownJsonSchemaIdentity(val url: String? = null, val fileName: String? = null)
  }
}

