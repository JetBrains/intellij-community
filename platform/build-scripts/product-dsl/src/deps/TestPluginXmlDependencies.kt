// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.deps

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.plugins.parser.impl.parseContentAndXIncludes
import org.jetbrains.intellij.build.productLayout.discovery.extractLegacyDepends
import java.nio.file.Files
import java.nio.file.Path

internal data class TestPluginXmlDependencies(
  val pluginDependencies: Set<PluginId> = emptySet(),
  val moduleDependencies: Set<ContentModuleName> = emptySet(),
)

internal fun readExistingTestPluginDependencies(pluginXmlPath: Path): TestPluginXmlDependencies {
  if (!Files.exists(pluginXmlPath)) {
    return TestPluginXmlDependencies()
  }
  val content = Files.readString(pluginXmlPath)
  return parseTestPluginXmlDependencies(content)
}

internal fun parseTestPluginXmlDependencies(content: String): TestPluginXmlDependencies {
  if (content.isBlank()) {
    return TestPluginXmlDependencies()
  }
  val parseResult = parseContentAndXIncludes(input = content.toByteArray(), locationSource = null)
  val pluginDeps = LinkedHashSet<PluginId>()
  for (dep in parseResult.pluginDependencies) {
    pluginDeps.add(PluginId(dep))
  }
  for (legacy in extractLegacyDepends(content)) {
    pluginDeps.add(legacy.pluginId)
  }
  val moduleDeps = parseResult.moduleDependencies.mapTo(LinkedHashSet()) { ContentModuleName(it) }
  return TestPluginXmlDependencies(pluginDependencies = pluginDeps, moduleDependencies = moduleDeps)
}
