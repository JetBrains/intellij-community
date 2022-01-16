// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.fus

import com.intellij.internal.statistic.config.EventLogExternalSendSettings
import com.intellij.internal.statistic.config.EventLogExternalSettings
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.BuildHelper

import java.nio.charset.StandardCharsets
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
    Files.write(list, download(context, metadataServiceUri(context).with {
      appendProductCode(context, it)
    }))
    return dir
  }

  private static String appendProductCode(BuildContext context, String uri) {
    def name = context.applicationInfo.productCode + '.json'
    return uri.endsWith('/') ? "$uri$name" : "$uri/$name"
  }

  private static byte[] download(BuildContext context, String url) {
    context.messages.info("Downloading " + url)
    return BuildHelper.getInstance(context).download.invokeWithArguments(url) as byte[]
  }

  private static String metadataServiceUri(BuildContext context) {
    String providerUri = appendProductCode(context, context.proprietaryBuildTools.featureUsageStatisticsProperties.metadataProviderUri)
    byte[] config = download(context, providerUri)
    context.messages.info("Parsing $providerUri")
    ApplicationInfoProperties appInfo = context.applicationInfo
    EventLogExternalSendSettings settings = EventLogExternalSettings.parseSendSettings(new InputStreamReader(new ByteArrayInputStream(config), StandardCharsets.UTF_8), "${appInfo.majorVersion}.${appInfo.minorVersion}")
    return settings.getEndpoint("metadata")
  }
}
