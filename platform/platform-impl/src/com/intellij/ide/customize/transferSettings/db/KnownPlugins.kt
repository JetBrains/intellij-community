// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.db

import com.intellij.ide.customize.transferSettings.TransferableIdeFeatureId
import com.intellij.ide.customize.transferSettings.models.BuiltInFeature
import com.intellij.ide.customize.transferSettings.models.FeatureInfo
import com.intellij.ide.customize.transferSettings.models.PluginFeature
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.PluginId

object KnownPlugins {
  val ReSharper: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.ReSharper, "ReSharper", isHidden = true)

  val Git: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.Git, "Git")
  val editorconfig: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.EditorConfig, "editorconfig")
  @Suppress("HardCodedStringLiteral")
  val WebSupport: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.WebSupport, "Web support", "HTML, CSS, JS")
  val Docker: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.Docker, "Docker")

  val CSharp: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.CSharp, "C#")
  val NuGet: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.NuGet, "NuGet")
  val TestExplorer: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.TestExplorer, "TestExplorer")
  val RunConfigurations: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.RunConfigurations, "Run Configurations")
  val Unity: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.Unity, "Unity")
  val LiveTemplates: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.LiveTemplates, "Live Templates")
  val SpellChecker: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.SpellChecker, "Spell Checker")
  val LanguageSupport: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.LanguageSupport, "Language Support")
  val DotNetDecompiler: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.DotNetDecompiler, ".NET Decompiler")
  val DatabaseSupport: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.DatabaseSupport, "Database Support")
  val TSLint: FeatureInfo =
    if (PluginManager.isPluginInstalled(PluginId.getId("tslint")))
      BuiltInFeature(TransferableIdeFeatureId.TsLint, "TSLint")
    else
      PluginFeature(TransferableIdeFeatureId.TsLint, "tslint", "TSLint")

  val Rust: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.RustSupport, "Rust Support")

  // Plugins

  val XAMLStyler: PluginFeature = PluginFeature(TransferableIdeFeatureId.XamlStyler, "xamlstyler.rider", "XAML Styler")
  val Ideolog: PluginFeature = PluginFeature(TransferableIdeFeatureId.Ideolog, "com.intellij.ideolog", "Ideolog (logging)")
  val IdeaVim: PluginFeature = PluginFeature(TransferableIdeFeatureId.IdeaVim, "IdeaVIM", "IdeaVIM")
  val TeamCity: PluginFeature = PluginFeature(TransferableIdeFeatureId.TeamCity, "Jetbrains TeamCity Plugin", "TeamCity")
  val NodeJSSupport: PluginFeature = PluginFeature(TransferableIdeFeatureId.NodeJsSupport, "NodeJS", "NodeJS support")

  val Monokai: PluginFeature = PluginFeature(TransferableIdeFeatureId.Monokai, "monokai-pro", "Monokai")
  val Solarized: PluginFeature = PluginFeature(TransferableIdeFeatureId.Solarized, "com.tylerthrailkill.intellij.solarized", "Solarized")

  val DummyBuiltInFeature: BuiltInFeature = BuiltInFeature(TransferableIdeFeatureId.DummyBuiltInFeature, "")
  val DummyPlugin: PluginFeature = PluginFeature(TransferableIdeFeatureId.DummyPlugin, "", "")
}