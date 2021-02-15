// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.fus

import com.intellij.internal.statistic.config.EventLogExternalSendSettings
import com.intellij.internal.statistic.config.EventLogExternalSettings
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.http.HttpStatus
import org.apache.http.client.methods.HttpGet
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.retry.Retry
import org.jetbrains.intellij.build.impl.retry.StopTrying

import java.nio.file.Files
import java.nio.file.Path

/**
 * Download a default version of feature usage statistics metadata to be bundled with IDE.
 */
@CompileStatic
final class StatisticsRecorderBundledMetadataProvider {
  private static final String EVENTS_SCHEME_JSON = 'events-scheme.json'

  static Path downloadMetadata(BuildContext context) {
    String eventsSchemeJson = EVENTS_SCHEME_JSON
    Path dir = context.paths.tempDir.resolve("events-scheme")
    String recorderId = context.proprietaryBuildTools.featureUsageStatisticsProperties.recorderId
    Path list = dir.resolve("resources/event-log-metadata/$recorderId/$eventsSchemeJson")
    Files.createDirectories(list.parent)
    Files.writeString(list, download(context, metadataServiceUri(context).with {
      appendProductCode(context, it)
    }))
    return dir
  }

  private static String appendProductCode(BuildContext context, String uri) {
    def name = context.applicationInfo.productCode + '.json'
    return uri.endsWith('/') ? "$uri$name" : "$uri/$name"
  }

  private static String download(BuildContext context, String uri) {
    new Retry(context.messages).call {
      HttpClientBuilder.create().build().withCloseable {
        context.messages.info("Downloading $uri")
        def response = it.execute(new HttpGet(uri))
        def content = EntityUtils.toString(response.getEntity(), ContentType.APPLICATION_JSON.charset)
        def responseCode = response.statusLine.statusCode
        if (responseCode != HttpStatus.SC_OK) {
          def error = new RuntimeException("$responseCode: $content")
          // server error, will retry
          if (responseCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR) throw error
          throw new StopTrying(error)
        }
        return content
      }
    }
  }

  @CompileDynamic
  private static String metadataServiceUri(BuildContext context) {
    String providerUri = appendProductCode(context, context.proprietaryBuildTools.featureUsageStatisticsProperties.metadataProviderUri)
    String config = download(context, providerUri)
    context.messages.info("Parsing $providerUri")
    ApplicationInfoProperties appInfo = context.applicationInfo
    EventLogExternalSendSettings settings = EventLogExternalSettings.parseSendSettings(new StringReader(config), "${appInfo.majorVersion}.${appInfo.minorVersion}")
    return settings.getEndpoint("metadata")
  }
}
