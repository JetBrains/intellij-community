// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.kotlin

import com.intellij.util.io.Decompressor
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.createBuildTasks
import org.jetbrains.intellij.build.impl.BuildUtils.checkedReplace
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.PluginVersionEvaluator
import org.jetbrains.intellij.build.impl.PluginVersionEvaluatorResult
import org.jetbrains.intellij.build.impl.consumeDataByPrefix
import org.jetbrains.intellij.build.impl.createBuildContext
import java.nio.file.Path

abstract class KotlinPluginBuilder(val kind : KotlinPluginKind = System.getProperty("kotlin.plugin.kind")?.let(KotlinPluginKind::valueOf) ?: KotlinPluginKind.IJ) {
  enum class KotlinPluginKind { IJ, AS, MI, Fleet }

  companion object {
    /**
     * Module which contains META-INF/plugin.xml
     */
    const val MAIN_KOTLIN_PLUGIN_MODULE: String = "kotlin.plugin"
    const val MAIN_FRONTEND_MODULE_NAME: String = "kotlin.frontend.split"

    val MODULES: List<String> = java.util.List.of(
      "kotlin.plugin.common",
      "kotlin.plugin.k2",
      "intellij.kotlin.base.util",
      "intellij.kotlin.base.indices",
      "intellij.kotlin.base.compilerConfiguration",
      "intellij.kotlin.base.plugin",
      "intellij.kotlin.base.psi",
      "intellij.kotlin.base.kdoc",
      "intellij.kotlin.base.platforms",
      "intellij.kotlin.base.jps",
      "intellij.kotlin.base.analysis.utils",
      "intellij.kotlin.base.compilerConfiguration.ui",
      "intellij.kotlin.compilerPlugins.parcelize",
      "intellij.kotlin.compilerPlugins.support",
      "intellij.kotlin.completion.api",
      "kotlin.completion.impl.shared",
      "intellij.kotlin.native",
      "kotlin.scripting",
      "kotlin.j2k.shared",
      "intellij.kotlin.plugin.updater",
      "intellij.kotlin.preferences",
      "intellij.kotlin.references",
      "intellij.kotlin.jvm.debugger.core",
      "kotlin.jvm-debugger.evaluation",
      "intellij.kotlin.jvm.debugger.eval4j",
      "intellij.kotlin.migration",
      "kotlin.inspections",
      "intellij.kotlin.codeInsight.utils",
      "intellij.kotlin.codeInsight.descriptions",
      "intellij.kotlin.compilerPlugins.support.bundled",
      "kotlin.jsr223",
      "intellij.kotlin.base.serialization"
    )

    private val KOTLIN_SCRIPTING_LIBRARIES = java.util.List.of(
      "kotlinc.kotlin-script-runtime",
      "kotlinc.kotlin-scripting-jvm"
    )

    private val MODULES_SHARED_WITH_CLIENT = java.util.List.of(
      "intellij.kotlin.base.codeInsight.minimal",
      "intellij.kotlin.highlighting.minimal"
    )

    private val LIBRARIES_UNPACKED = java.util.List.of(
      "kotlinc.analysis-api-platform-interface",
      "kotlinc.analysis-api",
      "kotlinc.analysis-api-impl-base",
      "kotlinc.kotlin-scripting-compiler-impl",
      "kotlinc.kotlin-scripting-common",
      "kotlinc.kotlin-scripting-dependencies",
      "kotlinc.kotlin-gradle-statistics",
      "kotlinc.analysis-api-k2",
      "kotlinc.kotlin-compiler-fir",
      "kotlinc.low-level-api-fir",
      "kotlinc.symbol-light-classes",
      "kotlin-metadata",
      "kotlinc.kotlin-build-tools-api",
      "kotlinc.kotlin-build-tools-impl",
      "kotlinc.kotlin-build-tools-cri-impl",
    ) + KOTLIN_SCRIPTING_LIBRARIES

    private val LIBRARIES = java.util.List.of(
      "kotlinc.kotlin-compiler-fe10",
      "kotlinc.kotlin-compiler-ir",
      "kotlinc.kotlin-jps-common",
      "vavr",
      "javax-inject",
    )

    private val COMPILER_PLUGINS = java.util.List.of(
      "kotlinc.allopen-compiler-plugin",
      "kotlinc.noarg-compiler-plugin",
      "kotlinc.sam-with-receiver-compiler-plugin",
      "kotlinc.assignment-compiler-plugin",
      "kotlinc.scripting-compiler-plugin",
      "kotlinc.kotlinx-serialization-compiler-plugin",
      "kotlinc.parcelize-compiler-plugin",
      "kotlinc.lombok-compiler-plugin",
      "kotlinc.compose-compiler-plugin",
      "kotlinc.js-plain-objects-compiler-plugin",
      "kotlinc.kotlin-dataframe-compiler-plugin",
    )
  }

  open fun kotlinPlugin(addition: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    return PluginLayout.plugin(MAIN_KOTLIN_PLUGIN_MODULE) { spec ->
      spec.directoryName = "Kotlin"
      spec.mainJarName = "kotlin-plugin.jar"

      for (moduleName in MODULES_SHARED_WITH_CLIENT) {
        spec.withModule(moduleName, "kotlin-plugin-shared.jar")
      }

      for (moduleName in MODULES) {
        spec.withModule(moduleName)
      }

      basePluginsAndLibraries(spec)

      spec.withProjectLibrary("kotlinc.kotlin-jps-plugin-classpath", "jps/kotlin-jps-plugin.jar")
      withKotlincInPluginDirectory(spec = spec)

      spec.withCustomVersion(PluginVersionEvaluator { _, ideBuildVersion, _ ->
        // in kt-branches we have own since and until versions
        val sinceBuild = System.getProperty("kotlin.plugin.since")
        val untilBuild = System.getProperty("kotlin.plugin.until")
        val sinceUntil = if (sinceBuild != null && untilBuild != null) sinceBuild to untilBuild else null
        if (ideBuildVersion.contains("IJ")) {
          // TC configurations that are inherited from AbstractKotlinIdeArtifact.
          // In this environment, ideBuildVersion equals to build number.
          // The ideBuildVersion looks like XXX.YYYY.ZZ-IJ
          val version = ideBuildVersion.replace("IJ", kind.toString())
          Span.current().addEvent("Kotlin plugin IJ version: $version")
          PluginVersionEvaluatorResult(pluginVersion = version, sinceUntil = sinceUntil)
        }
        else {
          // IJ installer configurations.
          PluginVersionEvaluatorResult(pluginVersion = "$ideBuildVersion-$kind", sinceUntil = sinceUntil)
        }
      })

      if (kind == KotlinPluginKind.AS) {
        spec.withRawPluginXmlPatcher { text, _ ->
          checkedReplace(
            oldText = text,
            regex = "<!-- IJ/AS-DEPENDENCY-PLACEHOLDER -->",
            newText = """<plugin id="com.intellij.modules.androidstudio"/>""",
          )
        }
      }

      addition?.invoke(spec)
    }
  }

  /** paired with [excludeKotlinLibraries] */
  fun basePluginsAndLibraries(spec: PluginLayout.PluginLayoutSpec) {
    for (libraryName in LIBRARIES_UNPACKED) {
      spec.withProjectLibraryUnpackedIntoJar(libraryName, spec.mainJarName)
    }
    for (library in COMPILER_PLUGINS) {
      spec.withProjectLibrary(library)
    }
    withKotlincKotlinCompilerCommonLibrary(spec, spec.mainModule)
    for (library in LIBRARIES) {
      spec.withProjectLibrary(library)
    }
  }

  /** paired with [basePluginsAndLibraries] */
  fun excludeKotlinLibraries(spec: PluginLayout.PluginLayoutSpec) {
    for (libraryName in LIBRARIES_UNPACKED) {
      spec.excludeProjectLibrary(libraryName)
    }
    for (library in COMPILER_PLUGINS) {
      spec.excludeProjectLibrary(library)
    }
    spec.excludeProjectLibrary("kotlinc.kotlin-compiler-common")
    for (library in LIBRARIES) {
      spec.excludeProjectLibrary(library)
    }
  }

  suspend fun build(home: Path, properties: ProductProperties) {
    val context = createBuildContext(
      setupTracer = true,
      projectHome = home,
      productProperties = properties,
      options = BuildOptions(enableEmbeddedFrontend = false)
    )
    createBuildTasks(context).buildNonBundledPlugins(listOf(MAIN_KOTLIN_PLUGIN_MODULE))
  }

  /**
   * A special plugin for JetBrains Client
   */
  fun kotlinFrontendPlugin(): PluginLayout {
    return PluginLayout.plugin(MAIN_FRONTEND_MODULE_NAME) { spec ->
      spec.withModules(MODULES_SHARED_WITH_CLIENT)
      spec.withProjectLibrary("kotlinc.kotlin-compiler-common")
    }
  }

  fun kotlinScriptingPlugin(addition: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    val mainModuleName = "kotlin.scripting-plugin"
    return PluginLayout.pluginAutoWithCustomDirName(mainModuleName) { spec ->
      spec.directoryName = "KotlinScripting"
      spec.mainJarName = "kotlin-scripting-plugin.jar"

      spec.withModule("kotlin.jsr223")

      withKotlincKotlinCompilerCommonLibrary(spec, mainModuleName)
      spec.withProjectLibrary("kotlinc.kotlin-compiler-fe10")
      withKotlincInPluginDirectory(spec = spec)

      addition?.invoke(spec)
    }
  }
}

private fun withKotlincKotlinCompilerCommonLibrary(spec: PluginLayout.PluginLayoutSpec, mainPluginModule: String) {
  val kotlincKotlinCompilerCommon = "kotlinc.kotlin-compiler-common"
  spec.withProjectLibrary(kotlincKotlinCompilerCommon)

  spec.withPatch { patcher, context ->
    val jars = context.outputProvider.findLibraryRoots(kotlincKotlinCompilerCommon, moduleLibraryModuleName = null)
    if (jars.size != 1) {
      throw IllegalStateException("$kotlincKotlinCompilerCommon is expected to have only one jar")
    }

    consumeDataByPrefix(jars[0], "META-INF/extensions/") { name, data ->
      patcher.patchModuleOutput(moduleName = mainPluginModule, path = name, content = data)
    }
  }
}

private fun withKotlincInPluginDirectory(libName: String = "kotlin-dist", target: String = "kotlinc", spec: PluginLayout.PluginLayoutSpec) {
  spec.withGeneratedResources { targetDir, context ->
    val distLibName = "kotlinc.$libName"
    val jars = context.outputProvider.findLibraryRoots(distLibName, moduleLibraryModuleName = null)
    if (jars.size != 1) {
      throw IllegalStateException("$distLibName is expected to have only one jar")
    }
    Decompressor.Zip(jars[0]).extract(targetDir.resolve(target))
  }
}

object CommunityKotlinPluginBuilder : KotlinPluginBuilder()
