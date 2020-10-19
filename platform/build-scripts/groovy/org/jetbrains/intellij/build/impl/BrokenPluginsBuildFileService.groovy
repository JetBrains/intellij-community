// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.execution.ParametersListUtil
import groovy.transform.CompileStatic
import org.apache.http.HttpStatus
import org.apache.http.client.methods.HttpGet
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildNumber
import org.jetbrains.intellij.build.impl.retry.Retry
import org.jetbrains.intellij.build.impl.retry.StopTrying

@CompileStatic
final class BrokenPluginsBuildFileService {
  BrokenPluginsBuildFileService(BuildContext context, LayoutBuilder layoutBuilder) {
    myBuildContext = context
    myLayout = layoutBuilder
  }

  private BuildContext myBuildContext
  private LayoutBuilder myLayout
  private static final String BROKEN_PLUGINS_FILE_NAME = "brokenPlugins.txt"
  private static final String MARKETPLACE_BROKEN_PLUGINS_URL = "/files/brokenPlugins.json"
  private Gson gson = new Gson()


  def buildFile() {
    myBuildContext.messages.progress("Start to build $BROKEN_PLUGINS_FILE_NAME")
    final String url =
      myBuildContext.proprietaryBuildTools.featureUsageStatisticsProperties.marketplaceHost + MARKETPLACE_BROKEN_PLUGINS_URL
    myBuildContext.messages.info("Get request for broken plugins, url: $url")
    List<MarketplaceBrokenPlugin> allBrokenPlugins = downloadFileFromMarketplace(url)
    Map<String, Set<String>> currentBrokenPlugins = filterBrokenPluginForCurrentIDE(allBrokenPlugins)
    storeBrokenPlugin(currentBrokenPlugins)
    myBuildContext.messages.info("$BROKEN_PLUGINS_FILE_NAME was updated.")
  }

  private List<MarketplaceBrokenPlugin> downloadFileFromMarketplace(String uri) {
    new Retry(myBuildContext.messages).call {
      HttpClientBuilder.create().build().withCloseable {
        myBuildContext.messages.info("Downloading $uri")
        def response = it.execute(new HttpGet(uri))
        def content = EntityUtils.toString(response.getEntity(), ContentType.APPLICATION_JSON.charset)
        def responseCode = response.statusLine.statusCode
        if (responseCode != HttpStatus.SC_OK) {
          def error = new RuntimeException("$responseCode: $content")
          // server error, will retry
          if (responseCode >= HttpStatus.SC_INTERNAL_SERVER_ERROR) throw error
          throw new StopTrying(error)
        }
        myBuildContext.messages.debug("Got the marketplace plugins. Data:\n $content")
        List<MarketplaceBrokenPlugin> plugins = gson.fromJson(content, new TypeToken<List<MarketplaceBrokenPlugin>>() {}.
          getType()) as List<MarketplaceBrokenPlugin>
        return plugins
      }
    }
  }

  private Map<String, Set<String>> filterBrokenPluginForCurrentIDE(List<MarketplaceBrokenPlugin> allBrokenPlugins) {
    final String currentBuildString = myBuildContext.buildNumber
    final BuildNumber currentBuild = BuildNumber.fromString(currentBuildString, currentBuildString)
    myBuildContext.messages.debug("Generate list of broken plugins for build:\n $currentBuild")
    def brokenPlugins = allBrokenPlugins
      .findAll {
        def originalUntil = BuildNumber.fromString(it.originalUntil, currentBuildString) ?: currentBuild
        def originalSince = BuildNumber.fromString(it.originalSince, currentBuildString) ?: currentBuild
        def until = BuildNumber.fromString(it.until, currentBuildString) ?: currentBuild
        def since = BuildNumber.fromString(it.since, currentBuildString) ?: currentBuild
        (originalSince <= currentBuild && currentBuild <= originalUntil) && (currentBuild > until || currentBuild < since)
      }
      .groupBy { it.id }
      .collectEntries { pluginId, bp ->
        [pluginId, bp.collect { it.version }.sort().toSet()]
      }
    myBuildContext.messages.debug("Broken plugin was generates. Count: ${brokenPlugins.size()}")
    return brokenPlugins as Map<String, Set<String>>
  }


  private storeBrokenPlugin(Map<String, Set<String>> brokenPlugin) {
    final String text = brokenPlugin.collect { id, versions ->
      "${escapeIfSpaces(id)} ${versions.collect { escapeIfSpaces(it) }.join(" ")}"
    }.join("\n")

    File patchedKeyMapDir = new File(myBuildContext.paths.temp, "patched-broken-plugins")
    File targetFile = new File(patchedKeyMapDir, "brokenPlugins.txt")
    myBuildContext.messages.info("Saving broken plugin into file ${targetFile.absolutePath}")
    FileUtil.createParentDirs(targetFile)
    targetFile.write(text)
    myLayout.patchModuleOutput("intellij.platform.resources", FileUtil.toSystemIndependentName(patchedKeyMapDir.absolutePath))
  }

  private static String escapeIfSpaces(String string) {
    return string.contains(" ") ? ParametersListUtil.escape(string) : string
  }

  @CompileStatic
  private class MarketplaceBrokenPlugin {
    String id
    String version
    String until
    String since
    String originalSince
    String originalUntil
  }
}

