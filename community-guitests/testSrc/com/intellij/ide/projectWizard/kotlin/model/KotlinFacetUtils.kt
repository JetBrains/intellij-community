// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.model

enum class TargetPlatform(private val stringValue: String) {
  JVM16("JVM 1.6"), JVM18("JVM 1.8"), JavaScript("JavaScript"), Common("Common (experimental)")
  ;

  companion object {
    fun valueFromString(value: String) = TargetPlatform.values().first { it.stringValue == value }
  }

  override fun toString() = this.stringValue
}

enum class LanguageVersion(private val stringValue: String) {
  Latest("Latest stable (%)"), L10("1.0"), L11("1.1"), L12("1.2")
  ;

  companion object {
    fun valueFromString(value: String) = LanguageVersion.values().first { it.stringValue.startsWith(value)}
  }

  override fun toString() = if (this.stringValue.contains("%")) this.stringValue.replace("%", versionFromPlugin.stringValue) else this.stringValue
}

enum class FacetCoroutineStatus(private val stringValue: String) {
  Warn("Enabled with warning"), Enable("Enabled"), Error("Disabled")
  ;

  companion object {
    fun valueFromString(value: String) = FacetCoroutineStatus.values().first { it.stringValue == value }
  }

  override fun toString() = this.stringValue
}

data class FacetStructure(
  val targetPlatform: TargetPlatform,
  val languageVersion: LanguageVersion,
  val apiVersion: LanguageVersion,
  val useProjectSettings: Boolean = false,
  val reportCompilerWarnings: Boolean = true,
  val coroutines: FacetCoroutineStatus = FacetCoroutineStatus.Warn,
  val cmdParameters: String = "-version",
  val jvmOptions: FacetStructureJVM? = null,
  val jsOptions: FacetStructureJS? = null
)

data class FacetStructureJVM(
    val templateClasses: String = "",
    val templatesClassPath: String = ""
)

enum class FacetJSEmbeddingSourceCode(private val stringValue: String) {
  Never("Never"),
  Always("Always"),
  Inline("When inlining a function from other module with embedded sources")
  ;

  companion object {
    fun valueFromString(value: String) = FacetJSEmbeddingSourceCode.values().first { it.stringValue == value }
  }

  override fun toString() = this.stringValue
}

enum class FacetJSModuleKind(private val stringValue: String) {
  Plain("Plain (put to global scope)"),
  AMD("AMD"),
  CommonJS("CommonJS"),
  UMD("UMD (detect AMD or CommonJS if available, fallback to plain)")
  ;

  companion object {
    fun valueFromString(value: String) = FacetJSModuleKind.values().first { it.stringValue == value }
  }

  override fun toString() = this.stringValue
}

data class FacetStructureJS(
  val generateSourceMap: Boolean = false,
  val sourceMapPrefix: String = "",
  val embedSourceCode2Map: FacetJSEmbeddingSourceCode = FacetJSEmbeddingSourceCode.Inline,
  val fileToPrepend: String = "",
  val fileToAppend: String = "",
  val copyLibraryRuntimeFiles: Boolean = true,
  val destinationDirectory: String = "lib",
  val moduleKind: FacetJSModuleKind = FacetJSModuleKind.Plain
)