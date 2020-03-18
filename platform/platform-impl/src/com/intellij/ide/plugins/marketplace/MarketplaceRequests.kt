// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.Url
import com.intellij.util.Urls.newFromEncoded
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.Internal
object MarketplaceRequests {

  private val mapper = ObjectMapper()

  @JvmStatic
  fun getBuildForPluginRepositoryRequests(): String {
    val instance = ApplicationInfoImpl.getShadowInstance()
    val compatibleBuild = PluginManagerCore.getPluginsCompatibleBuild()
    return if (compatibleBuild != null) {
      BuildNumber.fromStringWithProductCode(
        compatibleBuild,
        instance.build.productCode
      ).asString()
    }
    else instance.apiVersion
  }

  @JvmStatic
  @Throws(IOException::class)
  fun searchPlugins(query: String, count: Int): List<PluginNode> {
    val marketplaceSearchPluginData = HttpRequests.request(createSearchUrl(query, count)).connect {
      mapper.readValue(
        it.inputStream,
        object : TypeReference<List<MarketplaceSearchPluginData>>() {}
      )
    }
    return marketplaceSearchPluginData.map { it.toPluginNode() }
  }

  private fun createSearchUrl(query: String, count: Int): Url {
    val repoUrl = ApplicationInfoImpl.getShadowInstance().pluginManagerUrl
    return newFromEncoded(
      repoUrl + "/api/search/plugins?" + query + "&build=" +
      URLUtil.encodeURIComponent(getBuildForPluginRepositoryRequests()) + "&max=" + count
    )
  }
}
