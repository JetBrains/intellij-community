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

  val Java: BuiltInFeature = BuiltInFeature("Java")
  val Kotlin: BuiltInFeature = BuiltInFeature("Kotlin")
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
  val Maven: BuiltInFeature = BuiltInFeature("Maven")
  val Gradle: BuiltInFeature = BuiltInFeature("Gradle")
  val Debugger: BuiltInFeature = BuiltInFeature("Debugger")
  val WindowsSubsystemLinux: BuiltInFeature = BuiltInFeature("WSL")
  val Toml: BuiltInFeature = BuiltInFeature("TOML")
  val Vue: BuiltInFeature = BuiltInFeature("Vue.js")
  val AiAssistant: BuiltInFeature = BuiltInFeature("AI Assistant")

  // Language packs

  val ChineseLanguage: PluginFeature = PluginFeature("com.intellij.zh", "Chinese (Simplified) Language Pack / 中文语言包")
  val KoreanLanguage: PluginFeature = PluginFeature("com.intellij.ko", "Korean Language Pack / 한국어 언어 팩")
  val JapaneseLanguage: PluginFeature = PluginFeature("com.intellij.ja", "Japanese Language Pack / 日本語言語パック")

  // Plugins

  val XAMLStyler: PluginFeature = PluginFeature("xamlstyler.rider", "XAML Styler")
  val Ideolog: PluginFeature = PluginFeature("com.intellij.ideolog", "Ideolog (logging)")
  val IdeaVim: PluginFeature = PluginFeature("IdeaVIM", "IdeaVIM")
  val TeamCity: PluginFeature = PluginFeature("Jetbrains TeamCity Plugin", "TeamCity")
  val Scala: PluginFeature = PluginFeature("org.intellij.scala", "Scala")
  val Dart: PluginFeature = PluginFeature("Dart", "Dart")
  val Flutter: PluginFeature = PluginFeature("io.flutter", "Flutter")
  val Lombok: PluginFeature = PluginFeature("Lombook Plugin", "Lombok")
  val Prettier: PluginFeature = PluginFeature("intellij.prettierJS", "Prettier")
  val Kubernetes: PluginFeature = PluginFeature("com.intellij.kubernetes", "Kubernetes")

  val Monokai: PluginFeature = PluginFeature("monokai-pro", "Monokai")
  val Solarized: PluginFeature = PluginFeature("com.tylerthrailkill.intellij.solarized", "Solarized")

  val DummyBuiltInFeature: BuiltInFeature = BuiltInFeature("")
  val DummyPlugin: PluginFeature = PluginFeature("", "")
}
