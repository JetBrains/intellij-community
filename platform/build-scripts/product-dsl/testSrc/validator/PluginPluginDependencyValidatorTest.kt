// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.EDGE_BUNDLES
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.jetbrains.intellij.build.productLayout.graph.PluginGraphBuilder
import org.junit.jupiter.api.Test
import java.nio.file.Path

class PluginPluginDependencyValidatorTest {
  @Test
  fun `alias dependency satisfied when bundled by product`() {
    val builder = PluginGraphBuilder()
    val aliasId = PluginId("com.intellij.modules.xml")
    val pluginName = TargetName("plugin.a")

    val pluginInfo = PluginContentInfo(
      pluginXmlPath = Path.of("/tmp/plugin.a/plugin.xml"),
      pluginXmlContent = "",
      pluginId = PluginId("com.a"),
      contentModules = emptyList(),
      pluginDependencies = setOf(aliasId),
    )

    builder.addPluginWithContent(pluginName, pluginInfo, emptySet())
    builder.linkProductBundlesPlugin(productName = "IDEA", pluginName = pluginName, isTest = false)

    val aliasNodeName = TargetName("__alias__:${aliasId.value}")
    val aliasNodeId = builder.addPlugin(name = aliasNodeName, isTest = false, pluginId = aliasId)
    builder.addEdge(builder.addProduct("IDEA"), aliasNodeId, EDGE_BUNDLES)

    builder.addPluginDependencyEdges(mapOf(pluginName to pluginInfo))

    val graph = builder.build()
    val errors = validatePluginToPluginDependencies(graph)
    assertThat(errors).isEmpty()
  }
}
