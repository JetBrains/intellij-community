// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.io.ZipArchiver
import org.jetbrains.intellij.build.io.archiveDir
import org.jetbrains.intellij.build.io.writeNewZipWithoutIndex
import org.jetbrains.intellij.build.productRunner.runJavaForIntellijModule
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

internal const val BUILT_IN_HELP_MODULE_NAME = "intellij.builtInHelp"

internal fun buildHelpPlugin(pluginVersion: String, context: BuildContext): Pair<PluginLayout, String>? {
  val productName = context.applicationInfo.fullProductName
  val resourceRoot = context.paths.projectHome.resolve("help/plugin-resources")
  if (Files.notExists(resourceRoot.resolve("topics/app.js"))) {
    Span.current().addEvent("skip $productName Help plugin because $resourceRoot/topics/app.js not present")
    return null
  }

  val pluginXml = pluginXml(pluginVersion, context)
  return PluginLayout.pluginAutoWithCustomDirName(BUILT_IN_HELP_MODULE_NAME) { spec ->
    val productLowerCase = productName.replace(' ', '-').lowercase()
    spec.mainJarName = "$productLowerCase-help.jar"
    spec.directoryName = "${productName.replace(" ", "")}Help"
    spec.excludeFromModule(BUILT_IN_HELP_MODULE_NAME, "com/jetbrains/builtInHelp/indexer/**")
    spec.withPatch { patcher, _ ->
      patcher.patchModuleOutput(
        moduleName = BUILT_IN_HELP_MODULE_NAME,
        path = "META-INF/plugin.xml",
        content = pluginXml,
        overwrite = PatchOverwriteMode.TRUE
      )
    }
    spec.withProjectLibrary("lucene-core")
    spec.withGeneratedResources { targetDir, buildContext ->
      val assetJar = targetDir.resolve("lib/help-$productLowerCase-assets.jar")
      buildResourcesForHelpPlugin(
        resourceRoot = resourceRoot,
        classPath = buildContext.getModuleRuntimeClasspath(buildContext.findRequiredModule(BUILT_IN_HELP_MODULE_NAME), false),
        assetJar = assetJar,
        context = context,
      )
    }
  } to pluginXml
}

private fun pluginXml(version: String, context: BuildContext): String {
  val productName = context.applicationInfo.fullProductName
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
  <dependencies>
    <module name="intellij.libraries.lucene.common"/>
  </dependencies>
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


/*  Offline help plugins include a separate set of help topics for each of the supported languages.
This is a map of language code to descriptors that define resources associated with that language.
 */

private data class LanguageResourcesDescriptor(
  val isRequired: Boolean = false,
  val resPath: String,
  val resList: List<String> = listOf("topics", "images", "search"),
)

private val supportedLanguages = mapOf(
  //Localized resources don't include images
  Pair("zh-cn", LanguageResourcesDescriptor(resPath = "zh-cn/", resList = listOf("topics", "search"))),
  Pair("en-us", LanguageResourcesDescriptor(isRequired = true, resPath = ""))
)

private suspend fun buildResourcesForHelpPlugin(resourceRoot: Path, classPath: List<String>, assetJar: Path, context: CompilationContext) {
  spanBuilder("index help topics").use {
    helpIndexerMutex.withLock {
      supportedLanguages.forEach { (lang, descriptor) ->
        val topicPath = resourceRoot.resolve("${descriptor.resPath}topics")

        if (topicPath.exists())
          runJavaForIntellijModule(
            context = context, mainClass = "com.jetbrains.builtInHelp.indexer.HelpIndexer",
            args = listOf(
              resourceRoot.resolve("${descriptor.resPath}search").toString(),
              topicPath.toString(),
            ),
            jvmArgs = emptyList(),
            classPath = classPath,
          )
        else
          Span.current().addEvent("skip \"${lang}\" for offline help plugin because it was not supplied. ")
      }
    }

    writeNewZipWithoutIndex(file = assetJar, compress = true) { zipCreator ->
      val archiver = ZipArchiver()
      archiver.setRootDir(resourceRoot)

      supportedLanguages.forEach { (lang, descriptor) ->
        val langRootDir = resourceRoot.resolve(descriptor.resPath)
        if (langRootDir.exists()) {
          Span.current().addEvent("adding \"${lang}\" to the resulting ZIP.")
          descriptor.resList.forEach { resDir ->
            archiveDir(
              startDir = langRootDir.resolve(resDir),
              addFile = { archiver.addFile(it, zipCreator) })
          }
        }
        else
          Span.current().addEvent("skip adding \"${lang}\" to the resulting ZIP because it was not supplied.")
      }
    }
  }
}


