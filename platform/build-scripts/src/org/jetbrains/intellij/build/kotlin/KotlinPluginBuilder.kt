// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.kotlin

import com.intellij.util.io.Decompressor
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.LibraryPackMode
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.consumeDataByPrefix
import org.jetbrains.jps.model.library.JpsOrderRootType
import java.nio.file.Path
import java.util.regex.Pattern

object KotlinPluginBuilder {
  /**
   * Module which contains META-INF/plugin.xml
   */
  const val MAIN_KOTLIN_PLUGIN_MODULE: String = "kotlin.plugin"
  const val MAIN_FRONTEND_MODULE_NAME = "kotlin.frontend"

  val MODULES: List<String> = persistentListOf(
    "kotlin.plugin.common",
    "kotlin.plugin.k1",
    "kotlin.plugin.k2",
    "kotlin.base.util",
    "kotlin.base.indices",
    "kotlin.base.compiler-configuration",
    "kotlin.base.plugin",
    "kotlin.base.psi",
    "kotlin.base.kdoc",
    "kotlin.base.platforms",
    "kotlin.base.facet",
    "kotlin.base.project-structure",
    "kotlin.base.external-build-system",
    "kotlin.base.scripting",
    "kotlin.base.analysis-api-providers",
    "kotlin.base.analysis",
    "kotlin.base.code-insight",
    "kotlin.base.jps",
    "kotlin.base.analysis-api.utils",
    "kotlin.base.compiler-configuration-ui",
    "kotlin.base.obsolete-compat",
    "kotlin.base.statistics",
    "kotlin.base.fe10.plugin",
    "kotlin.base.fe10.analysis",
    "kotlin.base.fe10.analysis-api-providers",
    "kotlin.base.fe10.kdoc",
    "kotlin.base.fe10.code-insight",
    "kotlin.base.fe10.obsolete-compat",
    "kotlin.base.fe10.project-structure",
    "kotlin.core",
    "kotlin.ide",
    "kotlin.idea",
    "kotlin.fir.frontend-independent",
    "kotlin.jvm",
    "kotlin.compiler-reference-index",
    "kotlin.compiler-plugins.parcelize.common",
    "kotlin.compiler-plugins.parcelize.k1",
    "kotlin.compiler-plugins.parcelize.k2",
    "kotlin.compiler-plugins.parcelize.gradle",
    "kotlin.compiler-plugins.allopen.common-k1",
    "kotlin.compiler-plugins.allopen.gradle",
    "kotlin.compiler-plugins.allopen.maven",
    "kotlin.compiler-plugins.compiler-plugin-support.common",
    "kotlin.compiler-plugins.compiler-plugin-support.gradle",
    "kotlin.compiler-plugins.compiler-plugin-support.maven",
    "kotlin.compiler-plugins.kapt",
    "kotlin.compiler-plugins.kotlinx-serialization.common",
    "kotlin.compiler-plugins.kotlinx-serialization.gradle",
    "kotlin.compiler-plugins.kotlinx-serialization.maven",
    "kotlin.compiler-plugins.noarg.common",
    "kotlin.compiler-plugins.noarg.gradle",
    "kotlin.compiler-plugins.noarg.maven",
    "kotlin.compiler-plugins.sam-with-receiver.common",
    "kotlin.compiler-plugins.sam-with-receiver.gradle",
    "kotlin.compiler-plugins.sam-with-receiver.maven",
    "kotlin.compiler-plugins.assignment.common-k1",
    "kotlin.compiler-plugins.assignment.common-k2",
    "kotlin.compiler-plugins.assignment.gradle",
    "kotlin.compiler-plugins.assignment.maven",
    "kotlin.compiler-plugins.lombok.gradle",
    "kotlin.compiler-plugins.lombok.maven",
    "kotlin.compiler-plugins.scripting",
    "kotlin.compiler-plugins.android-extensions-stubs",
    "kotlin.completion.api",
    "kotlin.completion.impl-shared",
    "kotlin.completion.impl-k1",
    "kotlin.completion.impl-k2",
    "kotlin.maven",
    "kotlin.gradle.gradle-tooling",
    "kotlin.gradle.gradle",
    "kotlin.gradle.code-insight-common",
    "kotlin.gradle.gradle-java",
    "kotlin.gradle.code-insight-groovy",
    "kotlin.native",
    "kotlin.grazie",
    "kotlin.run-configurations.jvm",
    "kotlin.run-configurations.junit",
    "kotlin.run-configurations.junit-fe10",
    "kotlin.run-configurations.testng",
    "kotlin.formatter",
    "kotlin.repl",
    "kotlin.git",
    "kotlin.base.injection",
    "kotlin.injection",
    "kotlin.injection-k2",
    "kotlin.scripting",
    "kotlin.coverage",
    "kotlin.ml-completion",
    "kotlin.copyright",
    "kotlin.spellchecker",
    "kotlin.jvm-decompiler",
    "kotlin.j2k.k1.shared",
    "kotlin.j2k.k1.new.post-processing",
    "kotlin.j2k.old",
    "kotlin.j2k.old.post-processing",
    "kotlin.j2k.new",
    "kotlin.onboarding",
    "kotlin.plugin-updater",
    "kotlin.preferences",
    "kotlin.project-configuration",
    "kotlin.project-wizard.cli",
    "kotlin.project-wizard.core",
    "kotlin.project-wizard.idea",
    "kotlin.project-wizard.idea-k1",
    "kotlin.project-wizard.maven",
    "kotlin.project-wizard.gradle",
    "kotlin.project-wizard.compose",
    "kotlin.jvm-debugger.base.util",
    "kotlin.jvm-debugger.util",
    "kotlin.jvm-debugger.core",
    "kotlin.jvm-debugger.core-fe10",
    "kotlin.jvm-debugger.evaluation",
    "kotlin.jvm-debugger.coroutines",
    "kotlin.jvm-debugger.sequence",
    "kotlin.jvm-debugger.eval4j",
    "kotlin.uast.uast-kotlin-base",
    "kotlin.uast.uast-kotlin",
    "kotlin.uast.uast-kotlin-idea-base",
    "kotlin.uast.uast-kotlin-idea",
    "kotlin.i18n",
    "kotlin.migration",
    "kotlin.inspections",
    "kotlin.inspections-fe10",
    "kotlin.features-trainer",
    "kotlin.base.fir.analysis-api-providers",
    "kotlin.base.fir.code-insight",
    "kotlin.base.fir.project-structure",
    "kotlin.code-insight.api",
    "kotlin.code-insight.utils",
    "kotlin.code-insight.intentions-shared",
    "kotlin.code-insight.inspections-shared",
    "kotlin.code-insight.impl-base",
    "kotlin.code-insight.descriptions",
    "kotlin.code-insight.intentions-k1",
    "kotlin.code-insight.intentions-k2",
    "kotlin.code-insight.inspections-k1",
    "kotlin.code-insight.inspections-k2",
    "kotlin.code-insight.k1",
    "kotlin.code-insight.k2",
    "kotlin.code-insight.override-implement-shared",
    "kotlin.code-insight.override-implement-k1",
    "kotlin.code-insight.override-implement-k2",
    "kotlin.code-insight.live-templates-shared",
    "kotlin.code-insight.live-templates-k1",
    "kotlin.code-insight.live-templates-k2",
    "kotlin.code-insight.postfix-templates-k1",
    "kotlin.code-insight.postfix-templates-k2",
    "kotlin.code-insight.structural-search-k1",
    "kotlin.code-insight.structural-search-k2",
    "kotlin.code-insight.line-markers-shared",
    "kotlin.code-insight.line-markers-k2",
    "kotlin.fir",
    "kotlin.searching.k2",
    "kotlin.searching.base",
    "kotlin.highlighting.shared",
    "kotlin.highlighting.k1",
    "kotlin.highlighting.k2",
    "kotlin.uast.uast-kotlin-fir",
    "kotlin.uast.uast-kotlin-idea-fir",
    "kotlin.fir.fir-low-level-api-ide-impl",
    "kotlin.navigation",
    "kotlin.refactorings.common",
    "kotlin.refactorings.introduce.k2",
    "kotlin.refactorings.k2",
    "kotlin.refactorings.move.k2",
    "kotlin.refactorings.rename.k2",
    "kotlin.performanceExtendedPlugin",
    "kotlin.bundled-compiler-plugins-support",
  )

  private val MODULES_SHARED_WITH_CLIENT = persistentListOf(
    "kotlin.base.resources",
    "kotlin.base.code-insight.minimal",
    "kotlin.highlighting.minimal",
    "kotlin.formatter.minimal"
    )

  private val LIBRARIES = persistentListOf(
    "kotlinc.analysis-api-providers",
    "kotlinc.analysis-project-structure",
    "kotlinc.high-level-api",
    "kotlinc.high-level-api-fe10",
    "kotlinc.high-level-api-impl-base",
    "kotlinc.kotlin-script-runtime",
    "kotlinc.kotlin-scripting-compiler-impl",
    "kotlinc.kotlin-scripting-common",
    "kotlinc.kotlin-scripting-jvm",
    "kotlinc.kotlin-gradle-statistics",
    "kotlinc.high-level-api-fir",
    "kotlinc.kotlin-compiler-fir",
    "kotlinc.low-level-api-fir",
    "kotlinc.symbol-light-classes",
  )

  private val GRADLE_TOOLING_MODULES = persistentListOf(
    "kotlin.base.project-model",
    "kotlin.gradle.gradle-tooling.impl",
  )

  private val GRADLE_TOOLING_LIBRARIES = persistentListOf(
    "kotlin-gradle-plugin-idea",
    "kotlin-gradle-plugin-idea-proto",
    "kotlin-tooling-core",
  )

  private val COMPILER_PLUGINS = persistentListOf(
    "kotlinc.android-extensions-compiler-plugin",
    "kotlinc.allopen-compiler-plugin",
    "kotlinc.noarg-compiler-plugin",
    "kotlinc.sam-with-receiver-compiler-plugin",
    "kotlinc.assignment-compiler-plugin",
    "kotlinc.scripting-compiler-plugin",
    "kotlinc.kotlinx-serialization-compiler-plugin",
    "kotlinc.parcelize-compiler-plugin",
    "kotlinc.lombok-compiler-plugin",
  )

  @JvmStatic
  fun kotlinPlugin(ultimateSources: KotlinUltimateSources, addition: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    return kotlinPlugin(
      kind = KotlinPluginKind.valueOf(System.getProperty("kotlin.plugin.kind", "IJ")),
      ultimateSources = ultimateSources,
      addition = addition,
    )
  }

  @JvmStatic
  fun kotlinPlugin(kind: KotlinPluginKind, ultimateSources: KotlinUltimateSources, addition: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
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
        spec.withProjectLibrary(library, LibraryPackMode.STANDALONE_MERGED)
      }

      if (ultimateSources == KotlinUltimateSources.WITH_ULTIMATE_MODULES) {
        val ultimateModules = when (kind) {
          KotlinPluginKind.IJ -> persistentListOf(
            "kotlin-ultimate.common-native",
            "kotlin-ultimate.javascript.debugger",
            "kotlin-ultimate.javascript.nodeJs",
            "kotlin-ultimate.ultimate-plugin",
            "kotlin-ultimate.ultimate-native",
            "kotlin-ultimate.profiler",
          )
          KotlinPluginKind.Fleet -> persistentListOf(
            "kotlin-ultimate.common-native",
            "kotlin-ultimate.javascript.debugger",
            "kotlin-ultimate.javascript.nodeJs",
            "kotlin-ultimate.ultimate-fleet-plugin",
            "kotlin-ultimate.ultimate-native",
            //"kotlin-ultimate.profiler", FIXME: Causes perf problems in Fleet
          )
          else -> persistentListOf()
        }
        spec.withModules(ultimateModules)
      }

      val kotlincKotlinCompilerCommon = "kotlinc.kotlin-compiler-common"
      spec.withProjectLibrary(kotlincKotlinCompilerCommon, LibraryPackMode.STANDALONE_MERGED)

      spec.withPatch { patcher, context ->
        val library = context.project.libraryCollection.findLibrary(kotlincKotlinCompilerCommon)!!
        val jars = library.getFiles(JpsOrderRootType.COMPILED)
        if (jars.size != 1) {
          throw IllegalStateException("$kotlincKotlinCompilerCommon is expected to have only one jar")
        }

        consumeDataByPrefix(jars[0].toPath(), "META-INF/extensions/") { name, data ->
          patcher.patchModuleOutput(MAIN_KOTLIN_PLUGIN_MODULE, name, data)
        }
      }

      spec.withProjectLibrary("kotlinc.kotlin-compiler-fe10")
      spec.withProjectLibrary("kotlinc.kotlin-compiler-ir")

      spec.withProjectLibrary("kotlinc.kotlin-jps-plugin-classpath", "jps/kotlin-jps-plugin.jar")
      spec.withProjectLibrary("kotlinc.kotlin-jps-common")
      //noinspection SpellCheckingInspection
      spec.withProjectLibrary("javaslang", LibraryPackMode.STANDALONE_MERGED)
      spec.withProjectLibrary("kotlinx-collections-immutable", LibraryPackMode.STANDALONE_MERGED)
      spec.withProjectLibrary("javax-inject", LibraryPackMode.STANDALONE_MERGED)

      spec.withGeneratedResources { targetDir, context ->
        val distLibName = "kotlinc.kotlin-dist"
        val library = context.project.libraryCollection.findLibrary(distLibName)!!
        val jars = library.getFiles(JpsOrderRootType.COMPILED)
        if (jars.size != 1) {
          throw IllegalStateException("$distLibName is expected to have only one jar")
        }
        Decompressor.Zip(jars[0]).extract(targetDir.resolve("kotlinc"))
      }

      spec.withCustomVersion(object : PluginLayout.VersionEvaluator {
        override fun evaluate(pluginXml: Path, ideBuildVersion: String, context: BuildContext): String {
          val ijBuildNumber = Pattern.compile("^(\\d+)\\.([\\d.]+|\\d+\\.SNAPSHOT.*)\$").matcher(ideBuildVersion)
          if (ijBuildNumber.matches()) {
            // IJ installer configurations.
            // In this environment, ideBuildVersion matches ^(\d+)\.([\d.]+|\d+\.SNAPSHOT.*)\$
            return "$ideBuildVersion-$kind"
          }

          if (ideBuildVersion.contains("IJ")) {
            // TC configurations that are inherited from AbstractKotlinIdeArtifact.
            // In this environment, ideBuildVersion equals to build number.
            // The ideBuildVersion looks like XXX.YYYY.ZZ-IJ
            val version = ideBuildVersion.replace("IJ", kind.toString())
            context.messages.info("Kotlin plugin IJ version: $version")
            return version
          }

          throw IllegalStateException("Can't parse build number: $ideBuildVersion")
        }
      })

      spec.withPluginXmlPatcher { rawText ->
        val sinceBuild = System.getProperty("kotlin.plugin.since")
        val untilBuild = System.getProperty("kotlin.plugin.until")

        val text = if (sinceBuild != null && untilBuild != null) {
          // In kt-branches we have own since and until versions
          replace(rawText, "<idea-version.*?\\/>", "<idea-version since-build=\"${sinceBuild}\" until-build=\"${untilBuild}\"/>")
        }
        else {
          rawText
        }

        when (kind) {
          KotlinPluginKind.IJ, KotlinPluginKind.Fleet ->
            //noinspection SpellCheckingInspection
            replace(
              text,
              "<!-- IJ/AS-INCOMPATIBLE-PLACEHOLDER -->",
              "<incompatible-with>com.intellij.modules.androidstudio</incompatible-with>"
            )
          KotlinPluginKind.AS ->
            //noinspection SpellCheckingInspection
            replace(
              text,
              "<!-- IJ/AS-DEPENDENCY-PLACEHOLDER -->",
              "<plugin id=\"com.intellij.modules.androidstudio\"/>"
            )
          else -> throw IllegalStateException("Unknown kind = $kind")
        }
      }

      if (ultimateSources == KotlinUltimateSources.WITH_ULTIMATE_MODULES) {
        when (kind) {
          KotlinPluginKind.IJ, KotlinPluginKind.Fleet -> {
            // TODO KTIJ-11539 change to `System.getenv("TEAMCITY_VERSION") == null` later but make sure
            //  that `IdeaUltimateBuildTest.testBuild` passes on TeamCity
            val skipIfDoesntExist = true

            // Use 'DownloadAppCodeDependencies' run configuration to download LLDBFrontend
            spec.withBin("../CIDR/cidr-debugger/bin/lldb/linux/bin/LLDBFrontend", "bin/linux", skipIfDoesntExist)
            spec.withBin("../CIDR/cidr-debugger/bin/lldb/mac/LLDBFrontend", "bin/macos", skipIfDoesntExist)
            spec.withBin("../CIDR/cidr-debugger/bin/lldb/win/x64/bin/LLDBFrontend.exe", "bin/windows", skipIfDoesntExist)
            spec.withBin("../CIDR/cidr-debugger/bin/lldb/renderers", "bin/lldb/renderers")

            spec.withBin("../mobile-ide/common-native/scripts", "scripts")
          }
          else -> {}
        }
      }

      addition?.invoke(spec)
    }
  }

  private fun replace(oldText: String, regex: String, newText: String): String {
    val result = oldText.replaceFirst(Regex(regex), newText)
    if (result == oldText) {
      if (oldText.contains(newText) && !TeamCityHelper.isUnderTeamCity) {
        // Locally e.g. in 'Update IDE from Sources' allow data to be already present
        return result
      }

      throw IllegalStateException("Cannot find '$regex' in '$oldText'")
    }
    return result
  }

  suspend fun build(communityHome: BuildDependenciesCommunityRoot, home: Path, properties: ProductProperties) {
    val buildContext = BuildContextImpl.createContext(communityHome = communityHome,
                                                      setupTracer = true,
                                                      projectHome = home,
                                                      productProperties = properties)
    buildContext.options.enableEmbeddedJetBrainsClient = false
    BuildTasks.create(buildContext).buildNonBundledPlugins(listOf(MAIN_KOTLIN_PLUGIN_MODULE))
  }

  enum class KotlinUltimateSources {
    WITH_COMMUNITY_MODULES,
    WITH_ULTIMATE_MODULES,
  }

  enum class KotlinPluginKind {
    IJ, AS, MI, Fleet,
  }
}
