// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.providers.vscode.mappings

import com.intellij.ide.customize.transferSettings.db.KnownPlugins
import com.intellij.ide.customize.transferSettings.models.BuiltInFeature
import com.intellij.ide.customize.transferSettings.models.FeatureInfo
import com.intellij.ide.customize.transferSettings.models.PluginFeature
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.PlatformUtils
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * Allows to register plugins of third-party products for importing from VSCode.
 */
interface VSCodePluginMapping {

  companion object {
    val EP_NAME: ExtensionPointName<VSCodePluginMapping> = ExtensionPointName("com.intellij.transferSettings.vscode.pluginMapping")
  }

  fun mapPlugin(pluginId: String): FeatureInfo?
}

open class VSCodePluginMappingBase(private val map: Map<String, FeatureInfo>) : VSCodePluginMapping {

  override fun mapPlugin(pluginId: String): FeatureInfo? {
    return map[pluginId]
  }
}

@Suppress("SpellCheckingInspection")
private val commonPluginMap = mapOf(
  // Plugins
  "emilast.logfilehighlighter" to KnownPlugins.Ideolog,
  "xinyayang0506.log-analysis" to KnownPlugins.Ideolog,
  "vscodevim.vim" to KnownPlugins.IdeaVim,
  "msveden.teamcity-checker" to KnownPlugins.TeamCity,

  // Features
  "donjayamanne.githistory" to KnownPlugins.Git,
  "eamodio.gitlens" to KnownPlugins.Git,
  "waderyan.gitblame" to KnownPlugins.Git,
  "mhutchie.git-graph" to KnownPlugins.Git,
  "ritwickdey.liveserver" to KnownPlugins.WebSupport,
  "ms-azuretools.vscode-docker" to KnownPlugins.Docker,
  "ms-vscode-remote.remote-wsl" to KnownPlugins.WindowsSubsystemLinux,
  "bungcip.better-toml" to KnownPlugins.Toml,
  "Vue.volar" to KnownPlugins.Vue,
  //"GitHub.copilot-chat" to KnownPlugins.AiAssistant,

  // Language packs
  "ms-ceintl.vscode-language-pack-zh-hans" to KnownPlugins.ChineseLanguage,
  "ms-ceintl.vscode-language-pack-ja" to KnownPlugins.KoreanLanguage,
  "ms-ceintl.vscode-language-pack-ko" to KnownPlugins.JapaneseLanguage,

  // New mappings
  "streetsidesoftware.code-spell-checker" to KnownPlugins.SpellChecker,
  "formulahendry.code-runner" to KnownPlugins.RunConfigurations,
  "wayou.vscode-todo-highlight" to KnownPlugins.LanguageSupport,
  "editorconfig.editorconfig" to KnownPlugins.editorconfig,
  "ms-vscode.vscode-typescript-tslint-plugin" to KnownPlugins.TSLint,
  "github.vscode-pull-request-github" to KnownPlugins.Git,
  "mtxr.sqltools" to KnownPlugins.DatabaseSupport,
  "Dart-Code.dart-code" to KnownPlugins.Dart,
  "Dart-Code.flutter" to KnownPlugins.Flutter,
  "esbenp.prettier-vscode" to KnownPlugins.Prettier,
  "ms-kubernetes-tools.vscode-kubernetes-tools" to KnownPlugins.Kubernetes,
)

@Suppress("SpellCheckingInspection")
val JvmFeatures = mapOf(
  "vscjava.vscode-java-pack" to KnownPlugins.Java,
  "redhat.java" to KnownPlugins.Java,
  "vscjava.vscode-maven" to KnownPlugins.Maven,
  "vscjava.vscode-gradle" to KnownPlugins.Gradle,
  "vscjava.vscode-java-debug" to KnownPlugins.Debugger,
  "donjayamanne.javadebugger" to KnownPlugins.Debugger,
  "mathiasfrohlich.Kotlin" to KnownPlugins.Kotlin,
  "fwcd.kotlin" to KnownPlugins.Kotlin,
  "scala-lang.scala" to KnownPlugins.Scala,
  "vscjava.vscode-lombok" to KnownPlugins.Lombok,
)

@Suppress("SpellCheckingInspection", "unused") // used in Rider
val DotNetFeatures = mapOf(
  "ms-dotnettools.csharp" to KnownPlugins.CSharp,
  "jchannon.csharpextensions" to KnownPlugins.CSharp,
  "ms-dotnettools.csdevkit" to KnownPlugins.CSharp,
  "kreativ-software.csharpextensions" to KnownPlugins.CSharp,
  "jmrog.vscode-nuget-package-manager" to KnownPlugins.NuGet,
  "formulahendry.dotnet-test-explorer" to KnownPlugins.TestExplorer,
  "formulahendry.dotnet" to KnownPlugins.RunConfigurations,
  "unity.unity-debug" to KnownPlugins.Unity,
  "tobiah.unity-tools" to KnownPlugins.Unity,
  "kleber-swf.unity-code-snippets" to KnownPlugins.Unity,
  "jorgeserrano.vscode-csharp-snippets" to KnownPlugins.LiveTemplates,
  "fudge.auto-using" to KnownPlugins.CSharp,
  "k--kato.docomment" to KnownPlugins.CSharp,
  "icsharpcode.ilspy-vscode" to KnownPlugins.DotNetDecompiler,
)

@Serializable
private data class FeatureData(
  val vsCodeId: String,
  val vsCodeName: String,
  val ideaId: String?,
  val ideaName: String,
  val builtIn: Boolean,
  val bundled: Boolean,
  val disabled: Boolean
)

private val logger = logger<CommonPluginMapping>()

internal class CommonPluginMapping : VSCodePluginMapping {

  private fun getResourceMappings(): List<String> = when {
    PlatformUtils.isPyCharm() -> listOf("pc.json", "general.json")
    PlatformUtils.isRubyMine() -> listOf("rm.json", "general.json")
    else -> listOf("general.json")
  }

  val allPlugins by lazy {
    val resourceNames = getResourceMappings()
    val result = mutableMapOf<String, FeatureInfo>()
    for (resourceName in resourceNames) {
      logger.runAndLogException {
        @OptIn(ExperimentalSerializationApi::class)
        val features = this.javaClass.classLoader.getResourceAsStream("pluginData/$resourceName").use { file ->
          Json.decodeFromStream<List<FeatureData>>(file)
        }
        for (data in features) {
          val isBundled = data.bundled || data.builtIn
          val feature =
            if (isBundled) BuiltInFeature(null, data.ideaName)
            else {
              if (data.ideaId == null) {
                logger.error("Cannot determine IntelliJ plugin id for feature $data.")
                continue
              }
              PluginFeature(null, data.ideaId, data.ideaName)
            }
          result[data.vsCodeId.lowercase()] = feature
        }
      }
    }

    result
  }

  override fun mapPlugin(pluginId: String) = allPlugins[pluginId.lowercase()]
}

object PluginsMappings {

  fun pluginIdMap(pluginId: String): FeatureInfo? {

    for (mapping in VSCodePluginMapping.EP_NAME.extensionList) {
      val feature = mapping.mapPlugin(pluginId)
      if (feature != null) return feature
    }

    return null
  }
}
