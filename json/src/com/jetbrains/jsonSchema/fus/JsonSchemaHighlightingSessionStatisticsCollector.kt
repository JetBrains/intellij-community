// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.fus

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import kotlinx.coroutines.CancellationException

@Service(Service.Level.APP)
internal class JsonSchemaHighlightingSessionStatisticsCollector {
  companion object {
    @JvmStatic
    fun getInstance(): JsonSchemaHighlightingSessionStatisticsCollector {
      return service<JsonSchemaHighlightingSessionStatisticsCollector>()
    }
  }

  private inner class JsonSchemaHighlightingSession {
    val featuresWithCount = HashMap<JsonSchemaFusFeature, Int>()
    var schemaType: String? = null
    val requestedRemoteSchemas = HashSet<String>()
  }

  private val currentHighlightingSession = ThreadLocal<JsonSchemaHighlightingSession?>()

  fun recordSchemaFeaturesUsage(schemaRoot: JsonSchemaObject, operation: Runnable) {
    try {
      startCollectingSchemaHighlightingFus(schemaRoot)
      operation.run()
    }
    catch (exception: CancellationException) {
      // fast clean-up to avoid sending incomplete session data
      cleanupHighlightingSessionDataNoFlush()
      throw exception
    }
    finally {
      flushHighlightingSessionDataToFus()
    }
  }

  fun reportSchemaUsageFeature(featureKind: JsonSchemaFusCountedFeature) {
    val currentSession = getCurrentSession() ?: return
    currentSession.featuresWithCount[featureKind] = currentSession.featuresWithCount.getOrDefault(featureKind, 0) + 1
  }

  fun reportUniqueUrlDownloadRequestUsage(schemaUrl: String) {
    val currentSession = getCurrentSession() ?: return
    currentSession.requestedRemoteSchemas.add(schemaUrl)
  }

  private fun createNewSession(): JsonSchemaHighlightingSession {
    val newSession = JsonSchemaHighlightingSession()
    currentHighlightingSession.set(newSession)
    return newSession
  }

  private fun getCurrentSession(): JsonSchemaHighlightingSession? {
    return currentHighlightingSession.get()
  }

  private fun cleanupHighlightingSessionDataNoFlush() {
    currentHighlightingSession.set(null)
  }

  private fun startCollectingSchemaHighlightingFus(schemaRoot: JsonSchemaObject) {
    val currentSession = createNewSession()
    currentSession.schemaType = guessBestSchemaId(schemaRoot)
  }

  private fun flushHighlightingSessionDataToFus() {
    val sessionData = getCurrentSession()
    if (sessionData == null) {
      thisLogger().debug("No JSON schema highlighting session FUS to collect")
      return
    }

    val allCountEventsDuringSession = JsonSchemaFusCountedFeature.entries
      .map { feature -> feature to sessionData.featuresWithCount.getOrDefault(feature, 0) }
      .map { (feature, usagesCount) -> feature.event.with(usagesCount) }
    val uniqueSchemasCount = JsonSchemaFusCountedUniqueFeature.UniqueRemoteUrlDownloadRequest.event.with(sessionData.requestedRemoteSchemas.size)
    val schemaId = JsonSchemaFusAllowedListFeature.JsonFusSchemaId.event.with(sessionData.schemaType)

    val allDataAccumulated = allCountEventsDuringSession + uniqueSchemasCount + schemaId
    JsonFeatureUsageCollector.jsonSchemaHighlightingSessionData.log(allDataAccumulated)

    if (thisLogger().isDebugEnabled) {
      val printableStatistics = allDataAccumulated.joinToString(prefix = "\n", postfix = "\n", separator = "\n") { eventPair -> "${eventPair.field.name}: ${eventPair.data}" }
      thisLogger().debug("JSON schema highlighting session statistics: $printableStatistics")
    }
  }

  private fun guessBestSchemaId(schemaRoot: JsonSchemaObject): String? {
    val rawSchemaIdentifier = schemaRoot.id ?: schemaRoot.rawFile?.name
    return rawSchemaIdentifier?.replace("http://", "https://")
  }
}