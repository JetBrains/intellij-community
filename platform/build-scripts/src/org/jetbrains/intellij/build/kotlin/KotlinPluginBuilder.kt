// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.kotlin

import com.intellij.util.io.Decompressor
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.createBuildTasks
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.DataPluginVersionEvaluator
import org.jetbrains.intellij.build.impl.DescriptorMarker
import org.jetbrains.intellij.build.impl.DescriptorMarkerPatcher
import org.jetbrains.intellij.build.impl.PluginLayout
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
    const val MAIN_KOTLIN_PLUGIN_MODULE: String = "intellij.kotlin.plugin"
    const val MAIN_FRONTEND_MODULE_NAME: String = "kotlin.frontend.split"
    private const val SERIALIZATION_COMPILER_PLUGIN_MODULE = "intellij.libraries.kotlinc.kotlinx.serialization.compiler.plugin"

    private val MODULES_SHARED_WITH_CLIENT = java.util.List.of(
      "intellij.kotlin.base.codeInsight.minimal",
      "intellij.kotlin.highlighting.minimal"
    )

    private val KOTLINC_LIBRARY_MODULES = java.util.List.of(
      "intellij.libraries.kotlinc.analysis.api",
      "intellij.libraries.kotlinc.analysis.api.impl.base",
      "intellij.libraries.kotlinc.analysis.api.k2",
      "intellij.libraries.kotlinc.analysis.api.platform.interface",
      "intellij.libraries.kotlinc.kotlin.compiler.fir",
      "intellij.libraries.kotlinc.kotlin.jps.common",
      "intellij.libraries.kotlinc.kotlin.script.runtime",
      "intellij.libraries.kotlinc.kotlin.scripting.common",
      "intellij.libraries.kotlinc.kotlin.scripting.compiler.impl",
      "intellij.libraries.kotlinc.kotlin.scripting.dependencies",
      "intellij.libraries.kotlinc.kotlin.scripting.jvm",
      "intellij.libraries.kotlinc.low.level.api.fir",
      "intellij.libraries.kotlinc.symbol.light.classes",
      "intellij.libraries.kotlinc.scripting.compiler.plugin",
      "intellij.libraries.kotlinc.assignment.compiler.plugin",
    )

    private val LIBRARIES_UNPACKED = java.util.List.of(
      "kotlinc.kotlin-gradle-statistics",
      "kotlin-metadata",
      "kotlinc.kotlin-build-tools-api",
      "kotlinc.kotlin-build-tools-impl",
      "kotlinc.kotlin-build-tools-cri-impl",
    )

    private val LIBRARIES = java.util.List.of(
      "kotlinc.kotlin-compiler-fe10",
      "kotlinc.kotlin-compiler-ir",
      "vavr",
      "javax-inject",
    )

    private val COMPILER_PLUGINS = java.util.List.of(
      "kotlinc.allopen-compiler-plugin",
      "kotlinc.noarg-compiler-plugin",
      "kotlinc.sam-with-receiver-compiler-plugin",
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

      basePluginsAndLibraries(spec)

      spec.withProjectLibrary("kotlinc.kotlin-jps-plugin-classpath", "jps/kotlin-jps-plugin.jar")
      withKotlincInPluginDirectory(spec = spec)

      spec.withCustomVersion(KotlinPluginVersion(kind))

      if (kind == KotlinPluginKind.AS) {
        spec.withRawPluginXmlPatcher(DescriptorMarkerPatcher(listOf(DescriptorMarker(
          literal = "<!-- IJ/AS-DEPENDENCY-PLACEHOLDER -->",
          replacement = """<plugin id="com.intellij.modules.androidstudio"/>""",
        ))))
      }

      addition?.invoke(spec)
    }
  }

  /** paired with [excludeKotlinLibraries] */
  fun basePluginsAndLibraries(spec: PluginLayout.PluginLayoutSpec) {
    spec.withModules(KOTLINC_LIBRARY_MODULES)
    spec.withModule(
      SERIALIZATION_COMPILER_PLUGIN_MODULE,
      "kotlinc.kotlinx-serialization-compiler-plugin.jar",
    )
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
      spec.withModule(KOTLINC_KOTLIN_COMPILER_COMMON_MODULE, KOTLINC_KOTLIN_COMPILER_COMMON_JAR)
    }
  }

  fun kotlinScriptingPlugin(addition: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    val mainModuleName = "intellij.kotlin.jsr223.plugin"
    return PluginLayout.pluginAutoWithCustomDirName(mainModuleName) { spec ->
      spec.directoryName = "KotlinScripting"
      spec.mainJarName = "kotlin-scripting-plugin.jar"

      spec.withModule("intellij.kotlin.jsr223")

      withKotlincKotlinCompilerCommonLibrary(spec, mainModuleName)
      spec.withProjectLibrary("kotlinc.kotlin-compiler-fe10")
      withKotlincInPluginDirectory(spec = spec)

      addition?.invoke(spec)
    }
  }
}

private fun withKotlincKotlinCompilerCommonLibrary(spec: PluginLayout.PluginLayoutSpec, mainPluginModule: String) {
  val kotlincKotlinCompilerCommon = "kotlinc.kotlin-compiler-common"
  spec.withModule(KOTLINC_KOTLIN_COMPILER_COMMON_MODULE, KOTLINC_KOTLIN_COMPILER_COMMON_JAR)

  spec.withPatch { patcher, context ->
    val jars = context.outputProvider.findLibraryRoots(kotlincKotlinCompilerCommon, moduleLibraryModuleName = KOTLINC_KOTLIN_COMPILER_COMMON_MODULE)
    if (jars.size != 1) {
      throw IllegalStateException("$kotlincKotlinCompilerCommon is expected to have only one jar")
    }

    consumeDataByPrefix(jars[0], "META-INF/extensions/") { name, data ->
      patcher.patchModuleOutput(moduleName = mainPluginModule, path = name, content = data)
    }
  }
}

private const val KOTLINC_KOTLIN_COMPILER_COMMON_MODULE = "intellij.libraries.kotlinc.kotlin.compiler.common"
private const val KOTLINC_KOTLIN_COMPILER_COMMON_JAR = "intellij.libraries.kotlinc.kotlin.compiler.common.jar"

private fun withKotlincInPluginDirectory(libName: String = "kotlin-dist", target: String = "kotlinc", spec: PluginLayout.PluginLayoutSpec) {
  val distLibName = "kotlinc.$libName"
  spec.withGeneratedResources(inputProjectLibraries = listOf(distLibName)) { targetDir, context ->
    val jars = context.outputProvider.findLibraryRoots(distLibName, moduleLibraryModuleName = null)
    if (jars.size != 1) {
      throw IllegalStateException("$distLibName is expected to have only one jar")
    }
    Decompressor.Zip(jars[0]).extract(targetDir.resolve(target))
  }
}

object CommunityKotlinPluginBuilder : KotlinPluginBuilder()

/**
 * The Kotlin plugin's version: the IDE build version, then the plugin kind.
 *
 * A class and not a lambda, so that the dev-distribution descriptor plan can state [versionSuffix] as data. [evaluate]
 * keeps the two branches a released build needs. A `kotlin.plugin.since` and `kotlin.plugin.until` pair states a
 * compatibility range of its own, and a build version that already holds `IJ` takes the kind spliced in rather than
 * appended. A Bazel dev fragment sets no such property and stamps a build version of the `<baseline>.<date>.<counter>`
 * shape, so neither branch runs there. `checkProducedPluginDescriptor` refuses a produced descriptor whose version or
 * compatibility range is not the one the assembly computed, which is what makes a divergence loud.
 */
private class KotlinPluginVersion(private val kind: KotlinPluginBuilder.KotlinPluginKind) : DataPluginVersionEvaluator {
  override val versionSuffix: String
    get() = "-$kind"

  override suspend fun evaluate(
    pluginXmlSupplier: suspend () -> String,
    ideBuildVersion: String,
    context: BuildContext,
  ): PluginVersionEvaluatorResult {
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
      return PluginVersionEvaluatorResult(pluginVersion = version, sinceUntil = sinceUntil)
    }
    // IJ installer configurations.
    return PluginVersionEvaluatorResult(pluginVersion = "$ideBuildVersion$versionSuffix", sinceUntil = sinceUntil)
  }
}
