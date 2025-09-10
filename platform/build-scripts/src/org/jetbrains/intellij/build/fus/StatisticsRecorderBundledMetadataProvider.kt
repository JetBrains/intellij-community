// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.fus

import com.jetbrains.fus.reporting.configuration.ConfigurationClientFactory
import com.jetbrains.fus.reporting.configuration.ConfigurationClient
import com.google.gson.JsonParser
import com.jetbrains.fus.reporting.serialization.FusKotlinSerializer
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.downloadAsBytes
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.createSkippableJob
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import java.util.concurrent.CancellationException

/**
 * Download a default version of feature usage statistics metadata to be bundled with IDE.
 */
internal fun CoroutineScope.createStatisticsRecorderBundledMetadataProviderTask(moduleOutputPatcher: ModuleOutputPatcher,
                                                                                context: BuildContext): Job? {
  val featureUsageStatisticsPropertiesList = context.proprietaryBuildTools.featureUsageStatisticsProperties ?: return null
  return createSkippableJob(
    spanBuilder("bundle a default version of feature usage statistics"),
    stepId = BuildOptions.FUS_METADATA_BUNDLE_STEP,
    context = context
  ) {
    for (featureUsageStatisticsProperties in featureUsageStatisticsPropertiesList) {
      try {
        val recorderId = featureUsageStatisticsProperties.recorderId
        moduleOutputPatcher.patchModuleOutput(
          moduleName = "intellij.platform.ide.impl",
          path = "resources/event-log-metadata/$recorderId/events-scheme.json",
          content = download(metadataServiceUri(featureUsageStatisticsProperties, context))
        )
        val dictionaryListBytes = download(dictionaryServiceUri(featureUsageStatisticsProperties, context, "dictionaries.json"))
        val dictionariesListJson = JsonParser.parseString(String(dictionaryListBytes))
        val dictionariesList = dictionariesListJson.asJsonObject.get("dictionaries").asJsonArray

        if (!dictionariesList.isEmpty) {
          moduleOutputPatcher.patchModuleOutput(
            moduleName = "intellij.platform.ide.impl",
            path = "resources/event-log-metadata/$recorderId/dictionaries/dictionaries.json",
            content = dictionaryListBytes
          )
        }

        for (dictionary in dictionariesList) {
          val dictionaryName = dictionary.asString
          moduleOutputPatcher.patchModuleOutput(
            moduleName = "intellij.platform.ide.impl",
            path = "resources/event-log-metadata/$recorderId/dictionaries/$dictionaryName",
            content = download(dictionaryServiceUri(featureUsageStatisticsProperties, context, dictionaryName))
          )
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        // do not halt build, just record exception
        val span = Span.current()
        span.recordException(RuntimeException("Failed to bundle default version of feature usage statistics metadata", e))
        span.setStatus(StatusCode.ERROR)
      }
    }
  }
}

private fun appendProductCode(uri: String, context: BuildContext): String {
  val name = context.applicationInfo.productCode + ".json"
  return if (uri.endsWith('/')) "$uri$name" else "$uri/$name"
}

private suspend fun download(url: String): ByteArray {
  Span.current().addEvent("download", Attributes.of(AttributeKey.stringKey("url"), url))
  return downloadAsBytes(url)
}

private suspend fun serviceUri(featureUsageStatisticsProperties: FeatureUsageStatisticsProperties, context: BuildContext): ConfigurationClient {
  val providerUri = appendProductCode(featureUsageStatisticsProperties.metadataProviderUri, context)
  Span.current().addEvent("parsing", Attributes.of(AttributeKey.stringKey("url"), providerUri))
  val appInfo = context.applicationInfo
  val configurationClient = ConfigurationClientFactory.create(
    reader = download(providerUri).inputStream().reader(),
    productCode = context.applicationInfo.productCode,
    productVersion = "${appInfo.majorVersion}.${appInfo.minorVersion}",
    serializer = FusKotlinSerializer()
  )
  return configurationClient
}

private suspend fun metadataServiceUri(featureUsageStatisticsProperties: FeatureUsageStatisticsProperties, context: BuildContext): String
  = serviceUri(featureUsageStatisticsProperties, context).provideMetadataProductUrl()!!

private suspend fun dictionaryServiceUri(featureUsageStatisticsProperties: FeatureUsageStatisticsProperties, context: BuildContext, fileName: String): String
  = "${serviceUri(featureUsageStatisticsProperties, context).provideDictionaryEndpoint()!!}${featureUsageStatisticsProperties.recorderId}/$fileName"
