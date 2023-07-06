package com.intellij.ide.customize.transferSettings.providers.vscode.mappings

import com.intellij.ide.customize.transferSettings.db.KnownPlugins
import com.intellij.ide.customize.transferSettings.models.FeatureInfo

object PluginsMappings {
  private val theMap = mapOf(
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
    "vscjava.vscode-java-pack" to KnownPlugins.Java,
    "redhat.java" to KnownPlugins.Java,
    "vscjava.vscode-maven" to KnownPlugins.Maven,
    "vscjava.vscode-gradle" to KnownPlugins.Gradle,
    "vscjava.vscode-java-debug" to KnownPlugins.Debugger,
    "donjayamanne.javadebugger" to KnownPlugins.Debugger,
    "mathiasfrohlich.Kotlin" to KnownPlugins.Kotlin,
    "fwcd.kotlin" to KnownPlugins.Kotlin,
    "ms-vscode-remote.remote-wsl" to KnownPlugins.WindowsSubsystemLinux,
    "bungcip.better-toml" to KnownPlugins.Toml,
    "Vue.volar" to KnownPlugins.Vue,
    //"GitHub.copilot-chat" to KnownPlugins.AiAssistant,

    // Language packs
    "ms-ceintl.vscode-language-pack-zh-hans" to KnownPlugins.ChineseLanguage,
    "ms-ceintl.vscode-language-pack-ja" to KnownPlugins.KoreanLanguage,
    "ms-ceintl.vscode-language-pack-ko" to KnownPlugins.JapaneseLanguage,

    // New mappings
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
    "streetsidesoftware.code-spell-checker" to KnownPlugins.SpellChecker,
    "k--kato.docomment" to KnownPlugins.CSharp,
    "formulahendry.code-runner" to KnownPlugins.RunConfigurations,
    "wayou.vscode-todo-highlight" to KnownPlugins.LanguageSupport,
    "icsharpcode.ilspy-vscode" to KnownPlugins.DotNetDecompiler,
    "editorconfig.editorconfig" to KnownPlugins.editorconfig,
    "ms-vscode.vscode-typescript-tslint-plugin" to KnownPlugins.TSLint,
    "github.vscode-pull-request-github" to KnownPlugins.Git,
    "mtxr.sqltools" to KnownPlugins.DatabaseSupport,
    "scala-lang.scala" to KnownPlugins.Scala,
    "Dart-Code.dart-code" to KnownPlugins.Dart,
    "Dart-Code.flutter" to KnownPlugins.Flutter,
    "vscjava.vscode-lombok" to KnownPlugins.Lombok,
    "esbenp.prettier-vscode" to KnownPlugins.Prettier,
    "ms-kubernetes-tools.vscode-kubernetes-tools" to KnownPlugins.Kubernetes,
  )

  fun pluginIdMap(pluginId: String): FeatureInfo? = theMap[pluginId]

  fun idsList(): Collection<String> {
    return theMap.keys
  }

  fun originalPluginNameOverride(pluginId: String): Nothing? = when (pluginId) {
    //"emilast.logfilehighlighter" to "Log File Highlighter"
    else -> null
  }
}
