// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import com.intellij.util.xml.dom.readXmlAsModel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.SoftAssertions
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.collectPluginDescriptors
import java.nio.file.Path

fun runEssentialPluginsTest(
  homePath: Path,
  productProperties: ProductProperties,
  buildTools: ProprietaryBuildTools,
) = runBlocking {
  val buildContext = BuildContextImpl.createContext(
    projectHome = homePath,
    productProperties = productProperties,
    proprietaryBuildTools = buildTools,
    setupTracer = false,
    options = createBuildOptionsForTest(productProperties = productProperties, homeDir = homePath)
  )
  val essentialPlugins = readXmlAsModel(buildContext.appInfoXml.toByteArray()).children.filter { it.name == "essential-plugin" }.mapNotNull { it.content }
  val softly = SoftAssertions()
  println("Essential plugins: ${essentialPlugins.joinToString(", ")}")
  val pluginById = getPluginByIdMap(buildContext)
  for (essentialPlugin in essentialPlugins) {
    val essentialPluginDescription = pluginById[essentialPlugin]
    if(essentialPluginDescription == null) continue
    essentialPluginDescription.requiredDependencies.filter { it in pluginById }.forEach { requiredPlugin ->
      println("$essentialPlugin depends on $requiredPlugin")
      if (requiredPlugin !in essentialPlugins) {
        softly.fail<Unit>("$essentialPlugin depends on non-essential plugin $requiredPlugin")
      }
    }
  }
  softly.assertAll()
}

private data class PluginDescription(
  val pluginId: String,
  val requiredDependencies: Set<String> = emptySet()
)

private fun getPluginByIdMap(context: BuildContext): Map<String, PluginDescription> {
  val pluginMap = collectPluginDescriptors(
    skipImplementationDetailPlugins = true, //it's not possible to disable implementation detail plugin
    skipBundledPlugins = false,
    honorCompatiblePluginsToIgnore = false,
    context = context
  )
  return pluginMap.values.associate { it.id to PluginDescription(pluginId = it.id, requiredDependencies = it.requiredDependencies) }
}