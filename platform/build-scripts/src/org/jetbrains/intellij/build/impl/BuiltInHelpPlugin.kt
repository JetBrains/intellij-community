// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.telemetry.use
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.io.ZipArchiver
import org.jetbrains.intellij.build.io.archiveDir
import org.jetbrains.intellij.build.io.writeNewZipWithoutIndex
import org.jetbrains.intellij.build.productRunner.runJavaForIntellijModule
import java.nio.file.Files
import java.nio.file.Path

internal const val BUILT_IN_HELP_MODULE_NAME = "intellij.builtInHelp"
private val LUCENE_LIBRARIES = setOf("lucene-queryparser", "lucene-highlighter", "lucene-memory")

internal fun buildHelpPlugin(pluginVersion: String, context: BuildContext): PluginLayout? {
  val productName = context.applicationInfo.fullProductName
  val resourceRoot = context.paths.projectHome.resolve("help/plugin-resources")
  if (Files.notExists(resourceRoot.resolve("topics/app.js"))) {
    Span.current().addEvent("skip $productName Help plugin because $resourceRoot/topics/app.js not present")
    return null
  }

  return PluginLayout.plugin(BUILT_IN_HELP_MODULE_NAME) { spec ->
    val productLowerCase = productName.replace(' ', '-').lowercase()
    spec.mainJarName = "$productLowerCase-help.jar"
    spec.directoryName = "${productName.replace(" ", "")}Help"
    spec.excludeFromModule(BUILT_IN_HELP_MODULE_NAME, "com/jetbrains/builtInHelp/indexer/**")
    spec.doNotCopyModuleLibrariesAutomatically(listOf("jsoup"))
    spec.withGeneratedResources { targetDir, buildContext ->
      val assetJar = targetDir.resolve("lib/help-$productLowerCase-assets.jar")
      buildResourcesForHelpPlugin(
        resourceRoot = resourceRoot,
        classPath = buildContext.getModuleRuntimeClasspath(buildContext.findRequiredModule(BUILT_IN_HELP_MODULE_NAME), false),
        assetJar = assetJar,
        context = context,
      )
    }
    spec.withPatch { patcher, buildContext ->
      patcher.patchModuleOutput(moduleName = BUILT_IN_HELP_MODULE_NAME,
                                path = "META-INF/services/org.apache.lucene.codecs.Codec",
                                content = "org.apache.lucene.codecs.lucene50.Lucene50Codec")
      patcher.patchModuleOutput(moduleName = BUILT_IN_HELP_MODULE_NAME,
                                path = "META-INF/plugin.xml",
                                content = pluginXml(buildContext, pluginVersion),
                                overwrite = PatchOverwriteMode.TRUE)
    }
    LUCENE_LIBRARIES.forEach { spec.withProjectLibrary(it) }
  }
}

private fun pluginXml(buildContext: BuildContext, version: String): String {
  val productName = buildContext.applicationInfo.fullProductName
  val productLowerCase = productName.replace(" ", "-").lowercase()
  val pluginId = "bundled-$productLowerCase-help"
  val pluginName = "$productName Help"
  val productModuleDep = "com.intellij.modules.${productLowerCase.replace("intellij-", "")}"

  return """<idea-plugin allow-bundled-update="true">
  <name>$pluginName</name>
  <id>$pluginId</id>
  <version>$version</version>
  <idea-version since-build="${version.substring(0, version.lastIndexOf('.'))}"/>
  <vendor>JetBrains</vendor>
  <description>$productName Web Help for offline use: when help is invoked, pages are delivered via built-in Web server. In the plugin settings (Settings | Tools | $productName Help), you can opt to always use built-in help, even when Internet connection is available.</description>

  <depends>$productModuleDep</depends>
  <resource-bundle>messages.BuiltInHelpBundle</resource-bundle>

  <extensions defaultExtensionNs="com.intellij">
      <applicationService serviceInterface="com.intellij.openapi.help.HelpManager" overrides="true"
                          serviceImplementation="com.jetbrains.builtInHelp.BuiltInHelpManager" order="last"/>
      <httpRequestHandler implementation="com.jetbrains.builtInHelp.HelpSearchRequestHandler"/>
      <httpRequestHandler implementation="com.jetbrains.builtInHelp.HelpContentRequestHandler"/>
      <applicationConfigurable instance="com.jetbrains.builtInHelp.settings.SettingsPage"
                               displayName="$productName Help" groupId="tools"/>
  </extensions>
</idea-plugin>"""
}

/**
 * Required due to write lock in [org.apache.lucene.index.IndexWriter.IndexWriter]
 */
private val helpIndexerMutex = Mutex()

private suspend fun buildResourcesForHelpPlugin(resourceRoot: Path, classPath: List<String>, assetJar: Path, context: CompilationContext) {
  spanBuilder("index help topics").use {
    helpIndexerMutex.withLock {
      runJavaForIntellijModule(context = context, mainClass = "com.jetbrains.builtInHelp.indexer.HelpIndexer",
                               args = listOf(resourceRoot.resolve("search").toString(),
                            resourceRoot.resolve("topics").toString()),
                               jvmArgs = emptyList(),
                               classPath = classPath)
    }
    writeNewZipWithoutIndex(assetJar, compress = true) { zipCreator ->
      val archiver = ZipArchiver(zipCreator)
      archiver.setRootDir(resourceRoot)
      archiveDir(resourceRoot.resolve("topics"), archiver, null)
      archiveDir(resourceRoot.resolve("images"), archiver, null)
      archiveDir(resourceRoot.resolve("search"), archiver, null)
    }
  }
}
