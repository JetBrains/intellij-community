// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
      "kotlin.plugin.k1",
      "kotlin.plugin.k2",
      "intellij.kotlin.base.util",
      "intellij.kotlin.base.indices",
      "intellij.kotlin.base.compilerConfiguration",
      "intellij.kotlin.base.plugin",
      "intellij.kotlin.base.psi",
      "intellij.kotlin.base.kdoc",
      "intellij.kotlin.base.platforms",
      "intellij.kotlin.base.facet",
      "intellij.kotlin.base.projectStructure",
      "intellij.kotlin.base.externalSystem",
      "kotlin.base.scripting.k1",
      "intellij.kotlin.base.scripting",
      "intellij.kotlin.base.scripting.shared",
      "intellij.kotlin.base.scripting.scratch.definition",
      "intellij.kotlin.base.analysis.platform",
      "intellij.kotlin.base.analysis",
      "intellij.kotlin.base.codeInsight",
      "intellij.kotlin.base.jps",
      "intellij.kotlin.base.analysis.utils",
      "intellij.kotlin.base.compilerConfiguration.ui",
      "kotlin.base.obsolete-compat",
      "intellij.kotlin.base.statistics",
      "kotlin.base.fe10.plugin",
      "kotlin.base.fe10.analysis",
      "kotlin.base.fe10.analysis-api-platform",
      "kotlin.base.fe10.kdoc",
      "kotlin.base.fe10.code-insight",
      "kotlin.base.fe10.obsolete-compat",
      "kotlin.base.fe10.project-structure",
      "kotlin.core",
      "intellij.kotlin.ide",
      "kotlin.idea",
      "kotlin.fir.frontend-independent",
      "kotlin.jvm.shared",
      "intellij.kotlin.jvm",
      "kotlin.jvm.k1",
      "intellij.kotlin.compilerReferenceIndex",
      "intellij.kotlin.compilerPlugins.parcelize.common",
      "kotlin.compiler-plugins.parcelize.k1",
      "intellij.kotlin.compilerPlugins.parcelize",
      "kotlin.compiler-plugins.parcelize.gradle",
      "kotlin.compiler-plugins.allopen.common.k1",
      "kotlin.compiler-plugins.allopen.gradle",
      "intellij.kotlin.compilerPlugins.allopen.maven",
      "intellij.kotlin.compilerPlugins.support",
      "kotlin.compiler-plugins.compiler-plugin-support.gradle",
      "intellij.kotlin.compilerPlugins.support.maven",
      "intellij.kotlin.compilerPlugins.kapt",
      "kotlin.compiler-plugins.kotlinx-serialization.common",
      "kotlin.compiler-plugins.kotlinx-serialization.gradle",
      "intellij.kotlin.compilerPlugins.serialization",
      "intellij.kotlin.compilerPlugins.serialization.maven",
      "intellij.kotlin.compilerPlugins.dataframe.maven",
      "kotlin.compiler-plugins.noarg.common",
      "kotlin.compiler-plugins.noarg.gradle",
      "intellij.kotlin.compilerPlugins.noarg.maven",
      "kotlin.compiler-plugins.sam-with-receiver.common",
      "kotlin.compiler-plugins.sam-with-receiver.gradle",
      "intellij.kotlin.compilerPlugins.samWithReceiver.maven",
      "kotlin.compiler-plugins.assignment.common.k1",
      "intellij.kotlin.compilerPlugins.assignment.fixes",
      "kotlin.compiler-plugins.assignment.gradle",
      "intellij.kotlin.compilerPlugins.assignment.maven",
      "kotlin.compiler-plugins.lombok.gradle",
      "intellij.kotlin.compilerPlugins.lombok.maven",
      "intellij.kotlin.compilerPlugins.scripting",
      "kotlin.compiler-plugins.android-extensions-stubs",
      "intellij.kotlin.completion.api",
      "kotlin.completion.impl.shared",
      "kotlin.completion.impl.k1",
      "intellij.kotlin.completion.impl",
      "intellij.kotlin.maven",
      "intellij.kotlin.gradle.tooling",
      "intellij.kotlin.gradle.gradle",
      "intellij.kotlin.gradle.codeInsight.common",
      "kotlin.gradle.gradle-java",
      "kotlin.gradle.gradle-java.k1",
      "intellij.kotlin.gradle.java",
      "kotlin.gradle.scripting.k1",
      "intellij.kotlin.gradle.scripting",
      "kotlin.gradle.scripting.shared",
      "intellij.kotlin.gradle.codeInsight.groovy",
      "intellij.kotlin.gradle.codeInsight.toml",
      "intellij.kotlin.gradle.codeInsight.toml.k2",
      "intellij.kotlin.native",
      "intellij.kotlin.grazie",
      "intellij.kotlin.runConfigurations.jvm",
      "intellij.kotlin.runConfigurations.junit",
      "kotlin.run-configurations.junit-fe10",
      "intellij.kotlin.runConfigurations.testng",
      "intellij.kotlin.formatter",
      "kotlin.repl",
      "intellij.kotlin.git",
      "kotlin.base.injection",
      "kotlin.injection.k1",
      "intellij.kotlin.injection",
      "kotlin.scripting",
      "intellij.kotlin.coverage",
      "intellij.kotlin.completion.ml",
      "intellij.kotlin.copyright",
      "intellij.kotlin.spellchecker",
      "intellij.kotlin.jvm.decompiler",
      "kotlin.j2k.shared",
      "kotlin.j2k.k1.new.post-processing",
      "kotlin.j2k.k1.old",
      "kotlin.j2k.k1.old.post-processing",
      "kotlin.j2k.k1.new",
      "intellij.kotlin.j2k",
      "intellij.kotlin.onboarding",
      "intellij.kotlin.onboarding.gradle",
      "intellij.kotlin.plugin.updater",
      "intellij.kotlin.preferences",
      "intellij.kotlin.projectConfiguration",
      "intellij.kotlin.projectWizard.cli",
      "intellij.kotlin.projectWizard.core",
      "intellij.kotlin.projectWizard.idea",
      "kotlin.project-wizard.idea.k1",
      "intellij.kotlin.projectWizard.maven",
      "intellij.kotlin.projectWizard.gradle",
      "intellij.kotlin.projectWizard.compose",
      "intellij.kotlin.jvm.debugger.base.util",
      "intellij.kotlin.jvm.debugger.core",
      "kotlin.jvm-debugger.core-fe10",
      "kotlin.jvm-debugger.evaluation",
      "kotlin.jvm-debugger.evaluation.k1",
      "intellij.kotlin.jvm.debugger.evaluation",
      "intellij.kotlin.jvm.debugger.coroutines",
      "kotlin.jvm-debugger.sequence.k1",
      "intellij.kotlin.jvm.debugger.eval4j",
      "intellij.kotlin.uast.base",
      "kotlin.uast.uast-kotlin",
      "intellij.kotlin.uast.idea.base",
      "kotlin.uast.uast-kotlin-idea",
      "intellij.kotlin.i18n",
      "intellij.kotlin.migration",
      "kotlin.inspections",
      "kotlin.inspections-fe10",
      "intellij.kotlin.featuresTrainer",
      "intellij.kotlin.analysis.platform",
      "intellij.kotlin.codeInsight.base",
      "intellij.kotlin.projectStructure",
      "intellij.kotlin.scripting",
      "intellij.kotlin.codeInsight.api",
      "intellij.kotlin.codeInsight.utils",
      "kotlin.code-insight.intentions.shared",
      "kotlin.code-insight.inspections.shared",
      "intellij.kotlin.codeInsight.shared",
      "intellij.kotlin.codeInsight.descriptions",
      "intellij.kotlin.codeInsight.fixes",
      "kotlin.code-insight.intentions.k1",
      "intellij.kotlin.codeInsight.intentions",
      "kotlin.code-insight.inspections.k1",
      "intellij.kotlin.codeInsight.inspections",
      "kotlin.code-insight.k1",
      "intellij.kotlin.codeInsight",
      "kotlin.code-insight.override-implement.shared",
      "kotlin.code-insight.override-implement.k1",
      "intellij.kotlin.codeInsight.overrideImplement",
      "kotlin.code-insight.live-templates.shared",
      "kotlin.code-insight.live-templates.k1",
      "intellij.kotlin.codeInsight.liveTemplates",
      "kotlin.code-insight.postfix-templates.k1",
      "intellij.kotlin.codeInsight.postfixTemplates",
      "kotlin.code-insight.structural-search.k1",
      "intellij.kotlin.codeInsight.structuralSearch",
      "kotlin.code-insight.line-markers.shared",
      "intellij.kotlin.codeInsight.lineMarkers",
      "kotlin.fir",
      "intellij.kotlin.searching",
      "intellij.kotlin.searching.base",
      "kotlin.highlighting.shared",
      "kotlin.highlighting.k1",
      "intellij.kotlin.highlighting",
      "intellij.kotlin.uast",
      "intellij.kotlin.uast.idea",
      "intellij.kotlin.navigation",
      "kotlin.refactorings.common",
      "intellij.kotlin.refactorings",
      "intellij.kotlin.refactorings.move",
      "intellij.kotlin.refactorings.rename",
      "intellij.kotlin.performanceExtendedPlugin",
      "intellij.kotlin.compilerPlugins.support.bundled",
      "kotlin.jsr223",
      "intellij.kotlin.internal",
      "intellij.kotlin.base.serialization"
    )

    private val KOTLIN_SCRIPTING_LIBRARIES = java.util.List.of(
      "kotlinc.kotlin-script-runtime",
      "kotlinc.kotlin-scripting-jvm"
    )

    private val MODULES_SHARED_WITH_CLIENT = java.util.List.of(
      "intellij.kotlin.base.resources",
      "intellij.kotlin.base.codeInsight.minimal",
      "intellij.kotlin.highlighting.minimal",
      "intellij.kotlin.formatter.minimal"
    )

    private val LIBRARIES = java.util.List.of(
      "kotlinc.analysis-api-platform-interface",
      "kotlinc.analysis-api",
      "kotlinc.analysis-api-fe10",
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
    ) + KOTLIN_SCRIPTING_LIBRARIES

    private val GRADLE_TOOLING_MODULES = java.util.List.of(
      "intellij.kotlin.base.projectModel",
      "intellij.kotlin.gradle.tooling.impl",
    )

    private val GRADLE_TOOLING_LIBRARIES = java.util.List.of(
      "kotlin-gradle-plugin-idea",
      "kotlin-gradle-plugin-idea-proto",
      "kotlin-tooling-core",
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
      for (libraryName in LIBRARIES) {
        spec.withProjectLibraryUnpackedIntoJar(libraryName, spec.mainJarName)
      }

      val toolingJarName = "kotlin-gradle-tooling.jar"
      for (moduleName in GRADLE_TOOLING_MODULES) {
        spec.withModule(moduleName, toolingJarName)
      }
      for (library in GRADLE_TOOLING_LIBRARIES) {
        spec.withProjectLibraryUnpackedIntoJar(library, toolingJarName)
      }

      for (library in COMPILER_PLUGINS) {
        spec.withProjectLibrary(library)
      }

      withKotlincKotlinCompilerCommonLibrary(spec, MAIN_KOTLIN_PLUGIN_MODULE)

      spec.withProjectLibrary("kotlinc.kotlin-compiler-fe10")
      spec.withProjectLibrary("kotlinc.kotlin-compiler-ir")

      spec.withProjectLibrary("kotlinc.kotlin-jps-plugin-classpath", "jps/kotlin-jps-plugin.jar")
      spec.withProjectLibrary("kotlinc.kotlin-jps-common")
      //noinspection SpellCheckingInspection
      spec.withProjectLibrary("vavr")
      spec.withProjectLibrary("javax-inject")
      spec.withProjectLibrary("jackson-dataformat-toml")

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
