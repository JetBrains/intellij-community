// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.db

import com.intellij.ide.customize.transferSettings.models.BuiltInFeature
import com.intellij.ide.customize.transferSettings.models.PluginFeature

object KnownPlugins {
  val ReSharper = BuiltInFeature("ReSharper", isHidden = true)

  val Git = BuiltInFeature("Git")
  val editorconfig = BuiltInFeature("editorconfig")
  @Suppress("HardCodedStringLiteral")
  val WebSupport = BuiltInFeature("Web support", "HTML, CSS, JS")
  val Docker = BuiltInFeature("Docker")

  val CSharp = BuiltInFeature("C#")
  val NuGet = BuiltInFeature("NuGet")
  val TestExplorer = BuiltInFeature("TestExplorer")
  val RunConfigurations = BuiltInFeature("Run Configurations")
  val Unity = BuiltInFeature("Unity")
  val LiveTemplates = BuiltInFeature("Live Templates")
  val SpellChecker = BuiltInFeature("Spell Checker")
  val LanguageSupport = BuiltInFeature("Language Support")
  val DotNetDecompiler = BuiltInFeature(".NET Decompiler")
  val DatabaseSupport = BuiltInFeature("Database Support")
  val TSLint = BuiltInFeature("TSLint")

  // Plugins

  val XAMLStyler = PluginFeature("xamlstyler.rider", "XAML Styler")
  val Ideolog = PluginFeature("com.intellij.ideolog", "Ideolog (logging)")
  val IdeaVim = PluginFeature("IdeaVIM", "IdeaVIM")
  val TeamCity = PluginFeature("Jetbrains TeamCity Plugin", "TeamCity")
  val NodeJSSupport = PluginFeature("NodeJS", "NodeJS support")

  val Monokai = PluginFeature("monokai-pro", "Monokai")
  val Solarized = PluginFeature("com.tylerthrailkill.intellij.solarized", "Solarized")

  val DummyBuiltInFeature = BuiltInFeature("")
  val DummyPlugin = PluginFeature("", "")
}