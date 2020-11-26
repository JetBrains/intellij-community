// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildNumber
import org.jetbrains.intellij.build.impl.retry.Retry
import org.jetbrains.intellij.build.impl.retry.StopTrying

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.GZIPInputStream

@CompileStatic
final class BrokenPluginsBuildFileService {
  private static final String MARKETPLACE_BROKEN_PLUGINS_URL = "https://plugins.jetbrains.com/files/brokenPlugins.json"

  /**
   * Generate brokenPlugins.txt file using JetBrains Marketplace.
   */
  static void buildFile(@NotNull BuildContext buildContext) {
    List<MarketplaceBrokenPlugin> allBrokenPlugins = downloadFileFromMarketplace(buildContext)
    Map<String, Set<String>> currentBrokenPlugins = filterBrokenPluginForCurrentIDE(allBrokenPlugins, buildContext)
    storeBrokenPlugin(currentBrokenPlugins, buildContext)
  }

  private static @NotNull List<MarketplaceBrokenPlugin> downloadFileFromMarketplace(@NotNull BuildContext buildContext) {
    Gson gson = new Gson()
    try {
      new Retry(buildContext.messages).call {
        buildContext.messages.info("Load broken plugin list from $MARKETPLACE_BROKEN_PLUGINS_URL")
        HttpClient httpClient = HttpClient.newBuilder()
          .followRedirects(HttpClient.Redirect.ALWAYS)
          .build()

        HttpRequest request = HttpRequest.newBuilder(new URI(MARKETPLACE_BROKEN_PLUGINS_URL))
          .header("Accept", "application/json")
          .header("Accept-Encoding", "gzip")
          .build()

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        String encoding = response.headers().firstValue("Content-Encoding").orElse("")
        InputStream stream = response.body()
        switch (encoding) {
          case "":
            break
          case "gzip":
            stream = new GZIPInputStream(stream)
            break
          default:
            throw new UnsupportedOperationException("Unexpected Content-Encoding: " + encoding)
        }

        int responseCode = response.statusCode()
        String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
        if (responseCode != HttpURLConnection.HTTP_OK) {
          RuntimeException error = new RuntimeException("$responseCode: $content")
          // server error, will retry
          if (responseCode >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
            throw error
          }
          else {
            throw new StopTrying(error)
          }
        }

        buildContext.messages.debug("Got the marketplace plugins. Data:\n $content")
        List<MarketplaceBrokenPlugin> plugins = gson
          .<List<MarketplaceBrokenPlugin>>fromJson(content, new TypeToken<List<MarketplaceBrokenPlugin>>() {}.getType())
        return plugins
      }
    }
    catch (Exception e) {
      if (buildContext.options.isInDevelopmentMode) {
        buildContext.messages.warning(
          "Not able to get broken plugins info from JetBrains Marketplace: $e\n Assuming empty broken plugins list"
        )
        return []
      }
      else {
        throw e
      }
    }
  }

  private static Map<String, Set<String>> filterBrokenPluginForCurrentIDE(@NotNull List<MarketplaceBrokenPlugin> allBrokenPlugins,
                                                                          @NotNull BuildContext buildContext) {
    String currentBuildString = buildContext.buildNumber
    BuildNumber currentBuild = BuildNumber.fromString(currentBuildString, currentBuildString)
    buildContext.messages.debug("Generate list of broken plugins for build:\n $currentBuild")
    TreeMap<String, Set<String>> result = new TreeMap<String, Set<String>>()
    for (MarketplaceBrokenPlugin plugin : allBrokenPlugins) {
      BuildNumber originalUntil = BuildNumber.fromString(plugin.originalUntil, currentBuildString) ?: currentBuild
      BuildNumber originalSince = BuildNumber.fromString(plugin.originalSince, currentBuildString) ?: currentBuild
      BuildNumber until = BuildNumber.fromString(plugin.until, currentBuildString) ?: currentBuild
      BuildNumber since = BuildNumber.fromString(plugin.since, currentBuildString) ?: currentBuild
      if ((originalSince <= currentBuild && currentBuild <= originalUntil) && (currentBuild > until || currentBuild < since)) {
        result.computeIfAbsent(plugin.id, { new TreeSet<String>() }).add(plugin.version)
      }
    }
    buildContext.messages.debug("Broken plugin was generated (count=${result.size()})")
    return result
  }

  private static storeBrokenPlugin(@NotNull Map<String, Set<String>> brokenPlugin, @NotNull BuildContext buildContext) {
    Path targetFile = Paths.get(buildContext.paths.temp, "brokenPlugins.db")
    buildContext.messages.info("Saving broken plugin info into file $targetFile")
    Files.createDirectories(targetFile.parent)
    DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(targetFile), 32_000))
    try {
      out.write(1)
      out.writeInt(brokenPlugin.size())
      for (Map.Entry<String, Set<String>> entry : brokenPlugin.entrySet()) {
        out.writeUTF(entry.key)
        out.writeShort(entry.value.size())
        for (String version  : entry.value) {
          out.writeUTF(version)
        }
      }
    }
    finally {
      out.close()
    }
    buildContext.resourceFiles.add(targetFile)
  }

  @CompileStatic
  private static final class MarketplaceBrokenPlugin {
    String id
    String version
    String until
    String since
    String originalSince
    String originalUntil
  }
}

