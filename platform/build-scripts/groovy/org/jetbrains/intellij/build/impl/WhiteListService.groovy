// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.http.client.methods.HttpGet
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.LaxRedirectStrategy
import org.apache.http.util.EntityUtils
import org.jetbrains.intellij.build.BuildContext

@CompileStatic
class WhiteListService {
  private final BuildContext context
  private final String recorderId = 'FUS'
  private final String providerUri = "https://resources.jetbrains.com/storage/fus/config/$recorderId/lion-v3-assistant.xml"
  private final String whiteListJson = 'white-list.json'
  String includeInto = "resources/event-log-whitelist/$recorderId"

  WhiteListService(BuildContext context) {
    this.context = context
  }

  boolean shouldIncludeWhiteList(String moduleName) {
    moduleName == context.productProperties.productLayout.platformImplementationJarName
  }

  File whiteList() {
    def list = new File(context.paths.temp, whiteListJson)
    download(whiteListServiceUri().with {
      def name = context.applicationInfo.productCode + '.json'
      it.endsWith('/') ? "$it$name" : "$it/$name"
    }, list)
    list
  }

  private static void download(String uri, File file) {
    file.write HttpClientBuilder.create()
                 .setRedirectStrategy(LaxRedirectStrategy.INSTANCE)
                 .build().withCloseable {
      def response = it.execute(new HttpGet(uri))
      EntityUtils.toString(response.getEntity(), ContentType.APPLICATION_JSON.charset)
    }
  }

  @CompileDynamic
  private String whiteListServiceUri() {
    new XmlSlurper().parse(providerUri).'@white-list-service'.toString()
  }
}
