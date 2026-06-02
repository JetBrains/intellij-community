// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "DestructuringForParameter", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.ContentModuleFilter
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.impl.maven.MavenArtifactData
import org.jetbrains.intellij.build.impl.maven.MavenArtifactsBuilder
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import kotlin.text.contains

/**
 * @return module names which are required to run the necessary tools from build scripts
 */
private fun getToolModules(): List<String> = listOf("intellij.java.rt", "intellij.platform.starter", "intellij.tools.updater")

internal fun CoroutineScope.createMavenArtifactJob(platformLayout: PlatformLayout, context: BuildContext): Job? {
  val mavenArtifacts = context.productProperties.mavenArtifacts
  if (!mavenArtifacts.forIdeModules &&
      mavenArtifacts.additionalModules.isEmpty() &&
      mavenArtifacts.squashedModules.isEmpty() &&
      mavenArtifacts.proprietaryModules.isEmpty()) {
    return null
  }

  return createSkippableJob(spanBuilder("generate maven artifacts"), BuildOptions.MAVEN_ARTIFACTS_STEP, context) {
    val platformModules = HashSet<String>()
    if (mavenArtifacts.forIdeModules) {
      platformLayout.includedModules.mapTo(platformModules) { it.moduleName }
      platformModules.addAll(getToolModules())
      val enabledPluginModules = context.getBundledPluginModules()
      platformModules.addAll(enabledPluginModules)
      val pluginLayouts = getPluginLayoutsByJpsModuleNames(modules = enabledPluginModules, productLayout = context.productProperties.productLayout)
      val contentModuleFilter = context.getContentModuleFilter()
      for (plugin in pluginLayouts) {
        plugin.includedModules.mapTo(platformModules) { it.moduleName }
        val mainModule = context.outputProvider.findRequiredModule(plugin.mainModule)
        platformModules.addAll(readPluginIncompleteContentFromDescriptor(mainModule, contentModuleFilter))
      }
    }

    val mavenArtifactsBuilder = MavenArtifactsBuilder(context)
    val builtArtifacts = LinkedHashMap<MavenArtifactData, List<Path>>()
    if (!platformModules.isEmpty()) {
      mavenArtifactsBuilder.generateMavenArtifacts(
        moduleNamesToPublish = platformModules,
        outputDir = "maven-artifacts",
        builtArtifacts = builtArtifacts,
        ignoreNonMavenizable = true,
      )
    }
    if (!mavenArtifacts.additionalModules.isEmpty()) {
      mavenArtifactsBuilder.generateMavenArtifacts(
        moduleNamesToPublish = mavenArtifacts.additionalModules,
        moduleNamesToSquashAndPublish = mavenArtifacts.squashedModules,
        builtArtifacts = builtArtifacts,
        outputDir = "maven-artifacts"
      )
    }
    if (!mavenArtifacts.proprietaryModules.isEmpty()) {
      mavenArtifactsBuilder.generateMavenArtifacts(
        moduleNamesToPublish = mavenArtifacts.proprietaryModules,
        builtArtifacts = builtArtifacts,
        outputDir = "proprietary-maven-artifacts"
      )
    }
    for (spec in mavenArtifacts.aggregatorPomArtifacts) {
      mavenArtifactsBuilder.generateAggregatorPom(
        spec = spec,
        outputDir = "maven-artifacts",
        builtArtifacts = builtArtifacts,
      )
    }
    mavenArtifactsBuilder.validate(builtArtifacts)
  }
}

// The x-include is not resolved. If the plugin.xml includes any files, the content from these included files will not be considered.
private fun readPluginIncompleteContentFromDescriptor(pluginModule: JpsModule, contentModuleFilter: ContentModuleFilter): Sequence<String> {
  val pluginXml = findFileInModuleSources(pluginModule, "META-INF/plugin.xml") ?: return emptySequence()
  return readPluginContentFromDescriptor(readXmlAsModel(pluginXml)).mapNotNull { (moduleName, loadingRule) ->
    if (isOptionalLoadingRule(loadingRule) && !contentModuleFilter.isOptionalModuleIncluded(moduleName, pluginModule.name)) {
      return@mapNotNull null
    }
    moduleName
  }
}

internal fun readPluginContentFromDescriptor(pluginDescriptor: XmlElement): Sequence<Pair<String, String?>> {
  return sequence {
    for (content in pluginDescriptor.children("content")) {
      for (module in content.children("module")) {
        val moduleName = module.attributes.get("name")?.takeIf { !it.contains('/') } ?: continue
        val loadingRuleString = module.attributes.get("loading")
        yield(moduleName to loadingRuleString)
      }
    }
  }
}