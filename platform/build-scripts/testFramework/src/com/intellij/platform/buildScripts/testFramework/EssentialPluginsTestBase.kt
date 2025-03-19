// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import com.intellij.util.xml.dom.readXmlAsModel
import kotlinx.coroutines.Dispatchers
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
): Unit = runBlocking(Dispatchers.Default) {
  val buildContext = BuildContextImpl.createContext(
    homePath,
    productProperties,
    setupTracer = false,
    buildTools,
    createBuildOptionsForTest(productProperties, homePath)
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
        softly.fail("$essentialPlugin depends on non-essential plugin $requiredPlugin")
      }
    }
  }
  softly.assertAll()
}

private data class PluginDescription(
  val pluginId: String,
  val requiredDependencies: Set<String> = emptySet()
)

private suspend fun getPluginByIdMap(context: BuildContext): Map<String, PluginDescription> {
  val pluginMap = collectPluginDescriptors(
    skipImplementationDetails = true,  // it's not possible to disable implementation detail plugins
    skipBundled = false,
    honorCompatiblePluginsToIgnore = false,
    context
  )
  return pluginMap.values.associate { it.id to PluginDescription(it.id, it.requiredDependencies) }
}
