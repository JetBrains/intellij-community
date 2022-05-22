// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.fus

import com.intellij.internal.statistic.config.EventLogExternalSendSettings
import com.intellij.internal.statistic.config.EventLogExternalSettings
import groovy.transform.CompileStatic
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import kotlin.Unit
import kotlin.jvm.functions.Function0
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.impl.BuildHelperKt
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.TracerManager
import org.jetbrains.intellij.build.io.HttpKt

import java.nio.charset.StandardCharsets
import java.util.concurrent.ForkJoinTask
/**
 * Download a default version of feature usage statistics metadata to be bundled with IDE.
 */
@CompileStatic
final class StatisticsRecorderBundledMetadataProvider {
  @Nullable
  static ForkJoinTask<?> createTask(ModuleOutputPatcher moduleOutputPatcher, BuildContext context) {
    if (context.proprietaryBuildTools.featureUsageStatisticsProperties == null) {
      return null
    }

    return BuildHelperKt.createSkippableTask(
      TracerManager.spanBuilder("bundle a default version of feature usage statistics"),
      BuildOptions.FUS_METADATA_BUNDLE_STEP,
      context,
      new Function0<Unit>() {
        @Override
        Unit invoke() {
          try {
            String recorderId = context.proprietaryBuildTools.featureUsageStatisticsProperties.recorderId
            moduleOutputPatcher.patchModuleOutput("intellij.platform.ide.impl",
                                                  "resources/event-log-metadata/" + recorderId + "/events-scheme.json",
                                                  download(appendProductCode(context, metadataServiceUri(context))))
          }
          catch (Throwable e) {
            // do not halt build, just record exception
            Span span = Span.current()
            span.recordException(new RuntimeException("Failed to bundle default version of feature usage statistics metadata", e))
            span.setStatus(StatusCode.ERROR)
          }
          return null
        }
      }
    )
  }

  private static String appendProductCode(BuildContext context, String uri) {
    def name = context.applicationInfo.productCode + '.json'
    return uri.endsWith('/') ? "$uri$name" : "$uri/$name"
  }

  private static byte[] download(String url) {
    Span.current().addEvent("download", Attributes.of(AttributeKey.stringKey("url"), url))
    return HttpKt.download(url)
  }

  private static String metadataServiceUri(BuildContext context) {
    String providerUri = appendProductCode(context, context.proprietaryBuildTools.featureUsageStatisticsProperties.metadataProviderUri)
    byte[] config = download(providerUri)
    Span.current().addEvent("parsing", Attributes.of(AttributeKey.stringKey("url"), providerUri))
    ApplicationInfoProperties appInfo = context.applicationInfo
    EventLogExternalSendSettings settings = EventLogExternalSettings
      .parseSendSettings(new InputStreamReader(new ByteArrayInputStream(config), StandardCharsets.UTF_8),
                         "${appInfo.majorVersion}.${appInfo.minorVersion}")
    return settings.getEndpoint("metadata")
  }
}
