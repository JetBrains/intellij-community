// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.db

import com.intellij.ide.customize.transferSettings.models.BuiltInFeature
import com.intellij.ide.customize.transferSettings.models.PluginFeature

object KnownPlugins {
  val ReSharper: BuiltInFeature = BuiltInFeature("ReSharper", isHidden = true)

  val Git: BuiltInFeature = BuiltInFeature("Git")
  val editorconfig: BuiltInFeature = BuiltInFeature("editorconfig")
  @Suppress("HardCodedStringLiteral")
  val WebSupport: BuiltInFeature = BuiltInFeature("Web support", "HTML, CSS, JS")
  val Docker: BuiltInFeature = BuiltInFeature("Docker")

  val CSharp: BuiltInFeature = BuiltInFeature("C#")
  val NuGet: BuiltInFeature = BuiltInFeature("NuGet")
  val TestExplorer: BuiltInFeature = BuiltInFeature("TestExplorer")
  val RunConfigurations: BuiltInFeature = BuiltInFeature("Run Configurations")
  val Unity: BuiltInFeature = BuiltInFeature("Unity")
  val LiveTemplates: BuiltInFeature = BuiltInFeature("Live Templates")
  val SpellChecker: BuiltInFeature = BuiltInFeature("Spell Checker")
  val LanguageSupport: BuiltInFeature = BuiltInFeature("Language Support")
  val DotNetDecompiler: BuiltInFeature = BuiltInFeature(".NET Decompiler")
  val DatabaseSupport: BuiltInFeature = BuiltInFeature("Database Support")
  val TSLint: BuiltInFeature = BuiltInFeature("TSLint")

  // Plugins

  val XAMLStyler: PluginFeature = PluginFeature("xamlstyler.rider", "XAML Styler")
  val Ideolog: PluginFeature = PluginFeature("com.intellij.ideolog", "Ideolog (logging)")
  val IdeaVim: PluginFeature = PluginFeature("IdeaVIM", "IdeaVIM")
  val TeamCity: PluginFeature = PluginFeature("Jetbrains TeamCity Plugin", "TeamCity")
  val NodeJSSupport: PluginFeature = PluginFeature("NodeJS", "NodeJS support")

  val Monokai: PluginFeature = PluginFeature("monokai-pro", "Monokai")
  val Solarized: PluginFeature = PluginFeature("com.tylerthrailkill.intellij.solarized", "Solarized")

  val DummyBuiltInFeature: BuiltInFeature = BuiltInFeature("")
  val DummyPlugin: PluginFeature = PluginFeature("", "")
}