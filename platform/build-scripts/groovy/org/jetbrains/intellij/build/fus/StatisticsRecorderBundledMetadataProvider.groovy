// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.fus

import com.intellij.internal.statistic.config.EventLogExternalSettings
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.http.client.methods.HttpGet
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.retry.Retry

/**
 * Download a default version of feature usage statistics metadata to be bundled with IDE.
 */
@CompileStatic
class StatisticsRecorderBundledMetadataProvider {
  private static final String EVENTS_SCHEME_JSON = 'events-scheme.json'

  static File downloadMetadata(BuildContext context) {
    def eventsSchemeJson = EVENTS_SCHEME_JSON
    context.messages.block("Downloading a default version of feature usage statistics") {
      def dir = new File(context.paths.temp, 'events-scheme')
      def recorderId = context.proprietaryBuildTools.featureUsageStatisticsProperties.recorderId
      def list = new File(dir, "resources/event-log-metadata/$recorderId/$eventsSchemeJson")
      if (!list.parentFile.mkdirs() || !list.createNewFile()) {
        context.messages.error("Unable to create $list")
      }
      list.write new Retry(context.messages).call {
        download(context, metadataServiceUri(context).with {
          appendProductCode(context, it)
        })
      }
      return dir
    }
  }

  private static GString appendProductCode(BuildContext context, String uri) {
    def name = context.applicationInfo.productCode + '.json'
    return uri.endsWith('/') ? "$uri$name" : "$uri/$name"
  }

  private static String download(BuildContext context, String uri) {
    HttpClientBuilder.create().build().withCloseable {
      context.messages.info("Downloading $uri")
      def response = it.execute(new HttpGet(uri))
      return EntityUtils.toString(response.getEntity(), ContentType.APPLICATION_JSON.charset)
    }
  }

  @CompileDynamic
  private static String metadataServiceUri(BuildContext context) {
    def providerUri = appendProductCode(context, context.proprietaryBuildTools.featureUsageStatisticsProperties.metadataProviderUri)
    context.messages.info("Parsing $providerUri")

    def config = download(context, providerUri)
    def appInfo = context.applicationInfo
    def settings = EventLogExternalSettings.parseSendSettings(new StringReader(config), "${appInfo.majorVersion}.${appInfo.minorVersion}")
    return settings.getEndpoint("metadata")
  }
}
