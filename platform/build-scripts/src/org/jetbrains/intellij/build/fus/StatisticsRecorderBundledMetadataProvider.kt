// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.fus

import com.google.gson.JsonParser
import com.jetbrains.fus.reporting.FusJsonSerializer
import com.jetbrains.fus.reporting.configuration.ConfigurationClient
import com.jetbrains.fus.reporting.configuration.ConfigurationClientFactory
import com.jetbrains.fus.reporting.model.serialization.SerializationException
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
import org.jetbrains.intellij.build.lastModifiedFromHeadRequest
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import tools.jackson.core.JsonGenerator
import tools.jackson.core.StreamReadFeature
import tools.jackson.core.util.DefaultIndenter
import tools.jackson.core.util.DefaultPrettyPrinter
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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
          path = "event-log-metadata/$recorderId/events-scheme.json.meta",
          content = lastModified(metadataServiceUri(featureUsageStatisticsProperties, context))
        )
        moduleOutputPatcher.patchModuleOutput(
          moduleName = "intellij.platform.ide.impl",
          path = "event-log-metadata/$recorderId/events-scheme.json",
          content = download(metadataServiceUri(featureUsageStatisticsProperties, context))
        )
        val dictionaryListBytes = download(dictionaryServiceUri(featureUsageStatisticsProperties, context, "dictionaries.json"))
        val dictionariesListJson = JsonParser.parseString(String(dictionaryListBytes))
        val dictionariesList = dictionariesListJson.asJsonObject.get("dictionaries").asJsonArray

        if (!dictionariesList.isEmpty) {
          moduleOutputPatcher.patchModuleOutput(
            moduleName = "intellij.platform.ide.impl",
            path = "event-log-metadata/$recorderId/dictionaries/dictionaries.json.meta",
            content = lastModified(dictionaryServiceUri(featureUsageStatisticsProperties, context, "dictionaries.json"))
          )
          moduleOutputPatcher.patchModuleOutput(
            moduleName = "intellij.platform.ide.impl",
            path = "event-log-metadata/$recorderId/dictionaries/dictionaries.json",
            content = dictionaryListBytes
          )
        }

        for (dictionary in dictionariesList) {
          val dictionaryName = dictionary.asString
          moduleOutputPatcher.patchModuleOutput(
            moduleName = "intellij.platform.ide.impl",
            path = "event-log-metadata/$recorderId/dictionaries/$dictionaryName.meta",
            content = lastModified(dictionaryServiceUri(featureUsageStatisticsProperties, context, dictionaryName))
          )
          moduleOutputPatcher.patchModuleOutput(
            moduleName = "intellij.platform.ide.impl",
            path = "event-log-metadata/$recorderId/dictionaries/$dictionaryName",
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

val RFC1123_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")
val RFC1036_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd-MMM-yy HH:mm:ss zzz")
val ASCTIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy")
val DATE_FORMATS: Array<DateTimeFormatter> = arrayOf(RFC1123_FORMAT, RFC1036_FORMAT, ASCTIME_FORMAT)

private fun String.parseDate(): Long? {
  for (format in DATE_FORMATS) {
    try {
      return ZonedDateTime.parse(this, format).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
    }
  }
  return null
}

private suspend fun lastModified(url: String): ByteArray {
  Span.current().addEvent("last-modified", Attributes.of(AttributeKey.stringKey("url"), url))
  val dateTimeString = lastModifiedFromHeadRequest(url)
  val epochTime = dateTimeString?.parseDate() ?: 0L
  return epochTime.toString().toByteArray()
}

private suspend fun serviceUri(featureUsageStatisticsProperties: FeatureUsageStatisticsProperties, context: BuildContext): ConfigurationClient {
  val providerUri = appendProductCode(featureUsageStatisticsProperties.metadataProviderUri, context)
  Span.current().addEvent("parsing", Attributes.of(AttributeKey.stringKey("url"), providerUri))
  val appInfo = context.applicationInfo
  val configurationClient = ConfigurationClientFactory.create(
    reader = download(providerUri).inputStream().reader(),
    productCode = context.applicationInfo.productCode,
    productVersion = "${appInfo.majorVersion}.${appInfo.minorVersion}",
    serializer = FusJacksonSerializer()
  )
  return configurationClient
}

private suspend fun metadataServiceUri(featureUsageStatisticsProperties: FeatureUsageStatisticsProperties, context: BuildContext): String {
  val appInfo = context.applicationInfo
  val metadataVersion = (appInfo.majorVersion.substring(2,4) + appInfo.minorVersionMainPart).toInt()
  return serviceUri(featureUsageStatisticsProperties, context).provideMetadataProductUrl(metadataVersion)!!
}

private suspend fun dictionaryServiceUri(featureUsageStatisticsProperties: FeatureUsageStatisticsProperties, context: BuildContext, fileName: String): String
  = "${serviceUri(featureUsageStatisticsProperties, context).provideDictionaryEndpoint()!!}${featureUsageStatisticsProperties.recorderId}/$fileName"

class FusJacksonSerializer: FusJsonSerializer {
  private val SERIALIZATION_MAPPER: JsonMapper by lazy {
    JsonMapper
      .builder()
      .enable(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS)
      .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
      .defaultPrettyPrinter(CustomPrettyPrinter())
      .build()
  }

  private val DESERIALIZATION_MAPPER: JsonMapper by lazy {
    JsonMapper
      .builder()
      .enable(DeserializationFeature.USE_LONG_FOR_INTS)
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .build()
  }

  override fun toJson(data: Any): String = try {
    SERIALIZATION_MAPPER
      .writerWithDefaultPrettyPrinter()
      .writeValueAsString(data)
  } catch (e: Exception) {
    throw SerializationException(e)
  }

  override fun <T> fromJson(json: String, clazz: Class<T>): T = try {
    DESERIALIZATION_MAPPER
      .readValue(json, clazz)
  } catch (e: Exception) {
    throw SerializationException(e)
  }
}

private class CustomPrettyPrinter : DefaultPrettyPrinter {
  init {
    _objectIndenter = DefaultIndenter("  ", "\n")
    _arrayIndenter = DefaultIndenter("  ", "\n")
  }

  constructor() : super()
  constructor(base: DefaultPrettyPrinter?) : super(base)

  override fun writeObjectNameValueSeparator(g: JsonGenerator) {
    g.writeRaw(": ")
  }

  override fun writeEndArray(g: JsonGenerator, nrOfValues: Int) {
    if (!_arrayIndenter.isInline) {
      --_nesting
    }
    if (nrOfValues > 0) {
      _arrayIndenter.writeIndentation(g, _nesting)
    }
    g.writeRaw(']')
  }

  override fun writeEndObject(g: JsonGenerator, nrOfEntries: Int) {
    if (!_objectIndenter.isInline) {
      --_nesting
    }
    if (nrOfEntries > 0) {
      _objectIndenter.writeIndentation(g, _nesting)
    }
    g.writeRaw('}')
  }

  override fun createInstance(): DefaultPrettyPrinter {
    return CustomPrettyPrinter(this)
  }
}
