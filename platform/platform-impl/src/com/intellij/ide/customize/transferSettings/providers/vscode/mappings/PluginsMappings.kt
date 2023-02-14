package com.intellij.ide.customize.transferSettings.providers.vscode.mappings

import com.intellij.ide.customize.transferSettings.db.KnownPlugins

object PluginsMappings {
  private val theMap = mapOf(
    // Plugins
    "emilast.logfilehighlighter" to KnownPlugins.Ideolog,
    "xinyayang0506.log-analysis" to KnownPlugins.Ideolog,
    "vscodevim.vim" to KnownPlugins.IdeaVim,
    "msveden.teamcity-checker" to KnownPlugins.TeamCity,
    "ms-vscode.node-debug2" to KnownPlugins.NodeJSSupport,

    // Features
    "donjayamanne.githistory" to KnownPlugins.Git,
    "eamodio.gitlens" to KnownPlugins.Git,
    "waderyan.gitblame" to KnownPlugins.Git,
    "mhutchie.git-graph" to KnownPlugins.Git,
    "ritwickdey.liveserver" to KnownPlugins.WebSupport,
    "ms-azuretools.vscode-docker" to KnownPlugins.Docker,

    // New mappings
    "ms-dotnettools.csharp" to KnownPlugins.CSharp,
    "jchannon.csharpextensions" to KnownPlugins.CSharp,
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
    "mtxr.sqltools" to KnownPlugins.DatabaseSupport
  )

  fun pluginIdMap(pluginId: String) = theMap[pluginId]

  fun idsList(): Collection<String> {
    return theMap.keys
  }

  fun originalPluginNameOverride(pluginId: String) = when (pluginId) {
    //"emilast.logfilehighlighter" to "Log File Highlighter"
    else -> null
  }
}