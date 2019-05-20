// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.http.client.methods.HttpGet
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import org.jetbrains.intellij.build.BuildContext

/**
 * Download a default version of feature usage statistics white list to be bundled with IDE.
 */
@CompileStatic
class StatisticsRecorderBundledWhiteListProvider {
  private final BuildContext context
  private final String recorderId = 'FUS'
  private final String providerUri = "https://resources.jetbrains.com/storage/fus/config/$recorderId/lion-v3-assistant.xml"
  private final String whiteListJson = 'white-list.json'

  StatisticsRecorderBundledWhiteListProvider(BuildContext context) {
    this.context = context
  }

  File downloadWhiteList() {
    def dir = new File(context.paths.temp, 'whitelists')
    def list = new File(dir, "resources/event-log-whitelist/$recorderId/$whiteListJson")
    if (!list.parentFile.mkdirs() || !list.createNewFile()) {
      throw new IOException("Unable to create $list")
    }
    download(whiteListServiceUri().with {
      def name = context.applicationInfo.productCode + '.json'
      it.endsWith('/') ? "$it$name" : "$it/$name"
    }, list)
    return dir
  }

  private static void download(String uri, File file) {
    file.write HttpClientBuilder.create().build().withCloseable {
      def response = it.execute(new HttpGet(uri))
      EntityUtils.toString(response.getEntity(), ContentType.APPLICATION_JSON.charset)
    }
  }

  @CompileDynamic
  private String whiteListServiceUri() {
    new XmlSlurper().parse(providerUri).'@white-list-service'.toString()
  }
}
