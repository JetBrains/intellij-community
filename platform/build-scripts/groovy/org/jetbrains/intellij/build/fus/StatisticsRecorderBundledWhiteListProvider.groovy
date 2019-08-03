// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.fus

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.http.client.methods.HttpGet
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.retry.RetriableCall

/**
 * Download a default version of feature usage statistics white list to be bundled with IDE.
 */
@CompileStatic
class StatisticsRecorderBundledWhiteListProvider {
  private static final String WHITE_LIST_JSON = 'white-list.json'

  static File downloadWhiteList(BuildContext context) {
    def whiteListJson = WHITE_LIST_JSON
    context.messages.block("Downloading a default version of feature usage statistics") {
      def dir = new File(context.paths.temp, 'whitelists')
      def recorderId = context.proprietaryBuildTools.featureUsageStatisticsProperties.recorderId
      def list = new File(dir, "resources/event-log-whitelist/$recorderId/$whiteListJson")
      if (!list.parentFile.mkdirs() || !list.createNewFile()) {
        context.messages.error("Unable to create $list")
      }
      list.write new RetriableCall(context.messages).retry {
        download(context, whiteListServiceUri(context).with {
          def name = context.applicationInfo.productCode + '.json'
          it.endsWith('/') ? "$it$name" : "$it/$name"
        })
      }
      return dir
    }
  }

  private static String download(BuildContext context, String uri) {
    HttpClientBuilder.create().build().withCloseable {
      context.messages.info("Downloading $uri")
      def response = it.execute(new HttpGet(uri))
      return EntityUtils.toString(response.getEntity(), ContentType.APPLICATION_JSON.charset)
    }
  }

  @CompileDynamic
  private static String whiteListServiceUri(BuildContext context) {
    def providerUri = context.proprietaryBuildTools.featureUsageStatisticsProperties.whiteListProviderUri
    context.messages.info("Parsing $providerUri")
    new XmlSlurper().parse(providerUri).'@white-list-service'.toString()
  }
}
