// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.impl.*
import org.jetbrains.intellij.build.impl.PluginLayout.Companion.plugin
import org.jetbrains.intellij.build.impl.PluginLayout.Companion.pluginAuto
import org.jetbrains.intellij.build.impl.PluginLayout.Companion.pluginAutoWithDeprecatedCustomDirName
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.intellij.build.kotlin.KotlinPluginBuilder
import org.jetbrains.intellij.build.python.PythonCommunityPluginModules
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.useWithScope
import org.jetbrains.jps.model.library.JpsOrderRootType
import java.nio.file.Path
import java.util.*

object CommunityRepositoryModules {
  /**
   * Specifies non-trivial layout for all plugins that sources are located in 'community' and 'contrib' repositories
   */
  val COMMUNITY_REPOSITORY_PLUGINS: PersistentList<PluginLayout> = persistentListOf(
    pluginAuto("intellij.json") {},
    pluginAuto("intellij.yaml") { spec ->
      spec.withModule("intellij.yaml.editing", "yaml-editing.jar")
    },
    plugin("intellij.ant") { spec ->
      spec.mainJarName = "antIntegration.jar"
      spec.withModule("intellij.ant.jps", "ant-jps.jar")

      spec.withGeneratedResources { dir, buildContext ->
        copyAnt(pluginDir = dir, context = buildContext)
      }
    },
    plugin("intellij.laf.macos") { spec ->
      spec.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.MACOS)
    },
    plugin("intellij.webp") { spec ->
      spec.withPlatformBin(OsFamily.WINDOWS, JvmArchitecture.x64, "plugins/webp/lib/libwebp/win", "lib/libwebp/win")
      spec.withPlatformBin(OsFamily.MACOS, JvmArchitecture.x64, "plugins/webp/lib/libwebp/mac", "lib/libwebp/mac")
      spec.withPlatformBin(OsFamily.MACOS, JvmArchitecture.aarch64, "plugins/webp/lib/libwebp/mac", "lib/libwebp/mac")
      spec.withPlatformBin(OsFamily.LINUX, JvmArchitecture.x64, "plugins/webp/lib/libwebp/linux", "lib/libwebp/linux")
    },
    plugin("intellij.webp") { spec ->
      spec.bundlingRestrictions.marketplace = true
      spec.withResource("lib/libwebp/linux", "lib/libwebp/linux")
      spec.withResource("lib/libwebp/mac", "lib/libwebp/mac")
      spec.withResource("lib/libwebp/win", "lib/libwebp/win")
    },
    plugin("intellij.laf.win10") { spec ->
      spec.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.WINDOWS)
    },
    plugin("intellij.java.guiForms.designer") { spec ->
      spec.directoryName = "uiDesigner"
      spec.mainJarName = "uiDesigner.jar"
      spec.withModule("intellij.java.guiForms.jps", "jps/java-guiForms-jps.jar")
    },
    KotlinPluginBuilder.kotlinPlugin(KotlinPluginBuilder.KotlinUltimateSources.WITH_COMMUNITY_MODULES),
    pluginAuto(listOf("intellij.vcs.git")) { spec ->
      spec.withModule("intellij.vcs.git.rt", "git4idea-rt.jar")
    },
    pluginAuto(listOf("intellij.xpath")) { spec ->
      spec.withModule("intellij.xpath.rt", "rt/xslt-rt.jar")
    },
    pluginAuto(listOf("intellij.platform.langInjection", "intellij.java.langInjection", "intellij.xml.langInjection")) { spec ->
      spec.withModule("intellij.java.langInjection.jps")
    },
    pluginAutoWithDeprecatedCustomDirName("intellij.tasks.core") { spec ->
      spec.directoryName = "tasks"
      spec.withModule("intellij.tasks")
      spec.withModule("intellij.tasks.compatibility")
      spec.withModule("intellij.tasks.jira")
      spec.withModule("intellij.tasks.java")
    },
    pluginAuto(listOf("intellij.xslt.debugger")) { spec ->
      spec.withModule("intellij.xslt.debugger.rt", "xslt-debugger-rt.jar")
      spec.withModule("intellij.xslt.debugger.impl.rt", "rt/xslt-debugger-impl-rt.jar")
      spec.withModuleLibrary("Saxon-6.5.5", "intellij.xslt.debugger.impl.rt", "rt/saxon.jar")
      spec.withModuleLibrary("Saxon-9HE", "intellij.xslt.debugger.impl.rt", "rt/saxon9he.jar")
      spec.withModuleLibrary("Xalan-2.7.3", "intellij.xslt.debugger.impl.rt", "rt/xalan-2.7.3.jar")
      spec.withModuleLibrary("Serializer-2.7.3", "intellij.xslt.debugger.impl.rt", "rt/serializer-2.7.3.jar")
      spec.withModuleLibrary("RMI Stubs", "intellij.xslt.debugger.rt", "rmi-stubs.jar")
    },
    plugin("intellij.maven") { spec ->
      spec.withModule("intellij.idea.community.build.dependencies")
      spec.withModule("intellij.maven.jps")
      spec.withModule("intellij.maven.server.m3.common", "maven3-server-common.jar")
      spec.withModule("intellij.maven.server.m3.impl", "maven3-server.jar")
      spec.withModule("intellij.maven.server.m36.impl", "maven36-server.jar")
      spec.withModule("intellij.maven.server.m40", "maven40-server.jar")
      spec.withModule("intellij.maven.server.telemetry", "maven-server-telemetry.jar")
      spec.withModule("intellij.maven.errorProne.compiler")
      spec.withModule("intellij.maven.server.indexer", "maven-server-indexer.jar")
      spec.withModuleLibrary(libraryName = "apache.maven.core:3.8.3", moduleName = "intellij.maven.server.indexer",
                             relativeOutputPath = "intellij.maven.server.indexer/lib")
      spec.withModuleLibrary(libraryName = "apache.maven.wagon.provider.api:3.5.2", moduleName = "intellij.maven.server.indexer",
                             relativeOutputPath = "intellij.maven.server.indexer/lib")
      spec.withModuleLibrary(libraryName = "apache.maven.archetype.common-no-trans:3.2.1", moduleName = "intellij.maven.server.indexer",
                             relativeOutputPath = "intellij.maven.server.indexer/lib")
      spec.withModuleLibrary(libraryName = "apache.maven.archetype.catalog-no-trans:321", moduleName = "intellij.maven.server.indexer",
                             relativeOutputPath = "intellij.maven.server.indexer/lib")

      spec.withModule("intellij.maven.artifactResolver.m31", "artifact-resolver-m31.jar")
      spec.withModule("intellij.maven.artifactResolver.common", "artifact-resolver-m31.jar")

      spec.withModule("intellij.maven.server.eventListener", relativeJarPath = "maven-event-listener.jar")

      spec.doNotCopyModuleLibrariesAutomatically(listOf(
        "intellij.maven.artifactResolver.common",
        "intellij.maven.artifactResolver.m31",
        "intellij.maven.server.m3.common",
        "intellij.maven.server.m3.impl",
        "intellij.maven.server.m36.impl",
        "intellij.maven.server.m40",
        "intellij.maven.server.indexer",
      ))
      spec.withGeneratedResources { targetDir, context ->
        val targetLib = targetDir.resolve("lib")

        val maven4Libs = BundledMavenDownloader.downloadMaven4Libs(context.paths.communityHomeDirRoot)
        copyDir(maven4Libs, targetLib.resolve("maven4-server-lib"))

        val maven3Libs = BundledMavenDownloader.downloadMaven3Libs(context.paths.communityHomeDirRoot)
        copyDir(maven3Libs, targetLib.resolve("maven3-server-lib"))

        val mavenTelemetryDependencies = BundledMavenDownloader.downloadMavenTelemetryDependencies(context.paths.communityHomeDirRoot)
        copyDir(mavenTelemetryDependencies, targetLib.resolve("maven-telemetry-lib"))

        val mavenDist = BundledMavenDownloader.downloadMavenDistribution(context.paths.communityHomeDirRoot)
        copyDir(mavenDist, targetLib.resolve("maven3"))
      }
    },
    pluginAuto(listOf(
      "intellij.gradle",
      "intellij.gradle.common",
      "intellij.gradle.toolingProxy",
    )) { spec ->
      spec.withModule("intellij.gradle.toolingExtension", "gradle-tooling-extension-api.jar")
      spec.withModule("intellij.gradle.toolingExtension.impl", "gradle-tooling-extension-impl.jar")
      spec.withProjectLibrary("Gradle", LibraryPackMode.STANDALONE_SEPARATE)
      spec.withProjectLibrary("Ant", "ant", LibraryPackMode.STANDALONE_SEPARATE)
    },
    pluginAuto(listOf("intellij.gradle.java", "intellij.gradle.jps")) {
      it.excludeProjectLibrary("Ant")
      it.excludeProjectLibrary("Gradle")
    },
    pluginAuto("intellij.junit") { spec ->
      spec.withModule("intellij.junit.rt", "junit-rt.jar")
      spec.withModule("intellij.junit.v5.rt", "junit5-rt.jar")
    },
    plugin("intellij.testng") { spec ->
      spec.mainJarName = "testng-plugin.jar"
      spec.withModule("intellij.testng.rt", "testng-rt.jar")
      spec.withProjectLibrary("TestNG")
    },
    pluginAuto(listOf("intellij.devkit")) { spec ->
      spec.withModule("intellij.devkit.jps")
      spec.withModule("intellij.devkit.runtimeModuleRepository.jps")

      spec.bundlingRestrictions.includeInDistribution = PluginDistribution.NOT_FOR_PUBLIC_BUILDS
    },
    pluginAuto(listOf("intellij.eclipse")) { spec ->
      spec.withModule("intellij.eclipse.jps", "eclipse-jps.jar")
      spec.withModule("intellij.eclipse.common", "eclipse-common.jar")
    },
    plugin("intellij.java.coverage") { spec ->
      spec.withModule("intellij.java.coverage.rt")
      // explicitly pack JaCoCo as a separate JAR
      spec.withModuleLibrary("JaCoCo", "intellij.java.coverage", "jacoco.jar")
    },
    plugin("intellij.java.decompiler") { spec ->
      spec.directoryName = "java-decompiler"
      spec.mainJarName = "java-decompiler.jar"
      spec.withModule("intellij.java.decompiler.engine", spec.mainJarName)
    },
    javaFXPlugin("intellij.javaFX.community"),
    pluginAuto("intellij.terminal") { spec ->
      spec.withModule("intellij.terminal.completion")
      spec.withResource("resources/shell-integrations", "shell-integrations")
    },
    pluginAuto("intellij.emojipicker") { spec ->
      spec.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.LINUX)
    },
    pluginAuto(listOf("intellij.textmate")) { spec ->
      spec.withResource("lib/bundles", "lib/bundles")
    },
    PythonCommunityPluginModules.pythonCommunityPluginLayout(),
    androidDesignPlugin(),
    pluginAuto(listOf("intellij.completionMlRankingModels")) { spec ->
      spec.bundlingRestrictions.includeInDistribution = PluginDistribution.NOT_FOR_RELEASE
    },
    pluginAuto(listOf("intellij.statsCollector")) { spec ->
      spec.bundlingRestrictions.includeInDistribution = PluginDistribution.NOT_FOR_RELEASE
    },
    pluginAuto(listOf("intellij.lombok", "intellij.lombok.generated")),
    plugin("intellij.platform.testFramework.ui") { spec ->
      spec.withModuleLibrary("intellij.remoterobot.remote.fixtures", spec.mainModule, "")
      spec.withModuleLibrary("intellij.remoterobot.robot.server.core", spec.mainModule, "")
      spec.withProjectLibrary("okhttp")
    },
    pluginAuto(
      listOf(
        "intellij.performanceTesting",
        "intellij.tools.ide.starter.bus",
        "intellij.driver.model",
        "intellij.driver.impl",
        "intellij.driver.client"
      )
    ),
    pluginAuto(listOf("intellij.performanceTesting.ui")),
    githubPlugin("intellij.vcs.github.community", kind = "community"),
  )

  val CONTRIB_REPOSITORY_PLUGINS: List<PluginLayout> = java.util.List.of(
    pluginAuto("intellij.errorProne") { spec ->
      spec.withModule("intellij.errorProne.jps", "jps/errorProne-jps.jar")
    },
    pluginAuto("intellij.cucumber.java") { spec ->
      spec.withModule("intellij.cucumber.jvmFormatter", "cucumber-jvmFormatter.jar")
      spec.withModule("intellij.cucumber.jvmFormatter3", "cucumber-jvmFormatter3.jar")
      spec.withModule("intellij.cucumber.jvmFormatter4", "cucumber-jvmFormatter4.jar")
      spec.withModule("intellij.cucumber.jvmFormatter5", "cucumber-jvmFormatter5.jar")
    },
    pluginAuto("intellij.serial.monitor") { spec ->
      spec.withProjectLibrary("io.github.java.native.jssc", LibraryPackMode.STANDALONE_SEPARATE)
    },
  )

  private fun androidDesignPlugin(mainModuleName: String = "intellij.android.design-plugin.descriptor"): PluginLayout {
    return pluginAutoWithDeprecatedCustomDirName(mainModuleName) { spec ->
      spec.directoryName = "design-tools"
      spec.mainJarName = "design-tools.jar"

      // modules:
      // design-tools.jar
      spec.withModule("intellij.android.compose-designer")
      if (mainModuleName != "intellij.android.design-plugin.descriptor") {
        spec.withModule("intellij.android.design-plugin.descriptor")
      }
      spec.withModule("intellij.android.designer.customview")
      spec.withModule("intellij.android.designer")
      spec.withModule("intellij.android.glance-designer")
      spec.withModule("intellij.android.layoutlib")
      spec.withModule("intellij.android.nav.editor")
      spec.withModule("intellij.android.nav.editor.gradle")
      spec.withModule("intellij.android.preview-designer")
      spec.withModule("intellij.android.wear-designer")
      spec.withModule("intellij.android.motion-editor")

      // libs:
      spec.withProjectLibrary("layoutlib")

      // :libs

      //"resources": [
      //  "//prebuilts/studio/layoutlib:layoutlib",
      //  "//tools/adt/idea/compose-designer:kotlin-compiler-daemon-libs",
      //],
    }
  }

  fun androidPlugin(additionalModulesToJars: Map<String, String> = emptyMap(),
                    mainModuleName: String = "intellij.android.plugin.descriptor",
                    allPlatforms: Boolean = false,
                    addition: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null,): PluginLayout {
    return createAndroidPluginLayout(mainModuleName, additionalModulesToJars, allPlatforms, addition)
  }

  val supportedFfmpegPresets: PersistentList<SupportedDistribution> = persistentListOf(
    // todo notarization
    //SupportedDistribution(os = OsFamily.MACOS, arch = JvmArchitecture.x64),
    //SupportedDistribution(os = OsFamily.MACOS, arch = JvmArchitecture.aarch64),
    SupportedDistribution(os = OsFamily.WINDOWS, arch = JvmArchitecture.x64),
    SupportedDistribution(os = OsFamily.LINUX, arch = JvmArchitecture.x64),
  )

  private fun createAndroidPluginLayout(mainModuleName: String,
                                        additionalModulesToJars: Map<String, String> = emptyMap(),
                                        allPlatforms: Boolean,
                                        addition: ((PluginLayout.PluginLayoutSpec) -> Unit)?): PluginLayout =
    pluginAutoWithDeprecatedCustomDirName(mainModuleName) { spec ->
      spec.directoryName = "android"
      spec.mainJarName = "android.jar"
      spec.withCustomVersion { pluginXmlSupplier, ideBuildVersion, _ ->
        val pluginXml = pluginXmlSupplier()
        if (pluginXml.indexOf("<version>") != -1) {
          val declaredVersion = pluginXml.substring(pluginXml.indexOf("<version>") + "<version>".length, pluginXml.indexOf("</version>"))
          PluginVersionEvaluatorResult(pluginVersion = "$declaredVersion.$ideBuildVersion")
        }
        else {
          PluginVersionEvaluatorResult(pluginVersion = ideBuildVersion)
        }
      }

      spec.excludeProjectLibrary("Gradle")

      // modules:
      // adt-ui.jar
      spec.withModule("intellij.android.adt.ui.compose", "adt-ui.jar")
      spec.withModuleLibrary("jetbrains-jewel-int-ui-standalone", "intellij.android.adt.ui.compose", "jewel-int-ui-standalone.jar")
      spec.withModuleLibrary("jetbrains-jewel-ide-laf-bridge", "intellij.android.adt.ui.compose", "jewel-ide-laf-bridge.jar")
      spec.withModule("intellij.android.adt.ui.model", "adt-ui.jar")
      spec.withModule("intellij.android.adt.ui", "adt-ui.jar")

      // android-common.jar
      spec.withModule("intellij.android.common", "android-common.jar")
      spec.withModule("intellij.android.jps.model", "android-common.jar")

      // android-extensions-ide.jar
      spec.withModule("intellij.android.kotlin.extensions.common", "android-extensions-ide.jar")

      // android-kotlin-extensions-tooling.jar
      spec.withModule("intellij.android.kotlin.extensions.tooling", "android-kotlin-extensions-tooling.jar")

      //android-gradle-tooling.jar
      spec.withModule("intellij.android.gradle-tooling.api", "android-gradle.jar")
      spec.withModule("intellij.android.gradle-tooling.impl", "android-gradle.jar")
      spec.withModule("intellij.android.projectSystem.gradle.sync", "android-gradle.jar")

      // android-kotlin.jar
      spec.withModule("intellij.android.kotlin.extensions", "android-kotlin.jar")
      spec.withModule("intellij.android.kotlin.idea", "android-kotlin.jar")
      spec.withModule("intellij.android.kotlin.idea.common", "android-kotlin.jar")
      spec.withModule("intellij.android.kotlin.idea.k1", "android-kotlin.jar")
      spec.withModule("intellij.android.kotlin.idea.k2", "android-kotlin.jar")
      spec.withModule("intellij.android.kotlin.output.parser", "android-kotlin.jar")

      // android-profilers.jar
      spec.withModule("intellij.android.profilers.atrace", "android-profilers.jar")
      spec.withModule("intellij.android.profilers.ui", "android-profilers.jar")
      spec.withModule("intellij.android.profilers", "android-profilers.jar")
      spec.withModule("intellij.android.transportDatabase", "android-profilers.jar")

      // android-rt.jar
      //tools/adt/idea/rt:intellij.android.rt <= REMOVED

      // android-project-system-gradle-models.jar
      spec.withModule("intellij.android.projectSystem.gradle.models", "android-project-system-gradle-models.jar")

      // android.jar
      spec.withModule("intellij.android.analytics", "android.jar")
      spec.withModule("intellij.android.assistant", "android.jar")
      //tools/adt/idea/connection-assistant:connection-assistant <= REMOVED
      spec.withModule("intellij.android.adb", "android.jar")
      spec.withModule("intellij.android.adb.ui", "android.jar")
      spec.withModule("intellij.android.backup", "android.jar")
      spec.withModule("intellij.android.backup.api", "android.jar")
      spec.withModule("intellij.android.lint", "android.jar")
      spec.withModule("intellij.android.templates", "android.jar")
      spec.withModule("intellij.android.apkanalyzer", "android.jar")
      spec.withModule("intellij.android.app-inspection.api", "android.jar")
      spec.withModule("intellij.android.app-inspection.ide", "android.jar")
      spec.withModule("intellij.android.app-inspection.ide.gradle", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspector.api", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspector.ide", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.backgroundtask.ide", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.backgroundtask.model", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.backgroundtask.view", "android.jar")
      // Disabled sync it requires Google's APIs
      //spec.withModule("intellij.android.app-quality-insights.api", "android.jar")
      //spec.withModule("intellij.android.app-quality-insights.ide", "android.jar")
      //spec.withModule("intellij.android.app-quality-insights.ui", "android.jar")
      //spec.withModule("intellij.android.app-quality-insights.play-vitals.model", "android.jar")
      //spec.withModule("intellij.android.app-quality-insights.play-vitals.ide", "android.jar")
      //spec.withModule("intellij.android.app-quality-insights.play-vitals.view", "android.jar")
      spec.withModule("intellij.android.build-attribution", "android.jar")
      spec.withModule("intellij.android.compose-common", "android.jar")
      spec.withModule("intellij.android.device", "android.jar")
      spec.withModule("intellij.android.core", "android.jar")
      spec.withModule("intellij.android.navigator", "android.jar")
      spec.withModule("intellij.android.dagger", "android.jar")
      spec.withModule("intellij.android.databinding", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.database", "android.jar")
      spec.withModule("intellij.android.debuggers", "android.jar")
      spec.withModule("intellij.android.deploy", "android.jar")
      spec.withModule("intellij.android.device-explorer", "android.jar")
      spec.withModule("intellij.android.device-explorer-files", "android.jar")
      spec.withModule("intellij.android.device-explorer-monitor", "android.jar")
      spec.withModule("intellij.android.device-explorer-common", "android.jar")
      spec.withModule("intellij.android.device-manager", "android.jar")
      spec.withModule("intellij.android.device-manager-v2", "android.jar")
      spec.withModule("intellij.android.ml-api", "android.jar")
      // Packaged as a gradle-dsl plugin
      //tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl <= REMOVED
      //tools/adt/idea/gradle-dsl-kotlin:intellij.android.gradle.dsl.kotlin <= REMOVED
      //spec.withModule("intellij.android.gradle.dsl.declarative", "android.jar")
      //spec.withModule("intellij.android.gradle.dsl.toml", "android.jar")
      spec.withModule("intellij.android.lang-databinding", "android.jar")
      spec.withModule("intellij.android.lang", "android.jar")
      spec.withModule("intellij.android.layout-inspector", "android.jar")
      spec.withModule("intellij.android.layout-inspector.gradle", "android.jar")
      spec.withModule("intellij.android.layout-ui", "android.jar")
      spec.withModule("intellij.android.logcat", "android.jar")
      spec.withModule("intellij.android.mlkit", "android.jar")
      spec.withModule("intellij.android.nav.safeargs", "android.jar")
      spec.withModule("intellij.android.nav.safeargs.common", "android.jar")
      spec.withModule("intellij.android.nav.safeargs.common.gradle", "android.jar")
      spec.withModule("intellij.android.nav.safeargs.k1", "android.jar")
      spec.withModule("intellij.android.nav.safeargs.k2", "android.jar")
      spec.withModule("intellij.android.android-material", "android.jar")
      spec.withModule("intellij.android.observable.ui", "android.jar")
      spec.withModule("intellij.android.observable", "android.jar")
      if (mainModuleName != "intellij.android.plugin.descriptor") {
        spec.withModule("intellij.android.plugin.descriptor", "android.jar")
      }
      spec.withModule("intellij.android.preview-elements", "android.jar")
      spec.withModule("intellij.android.profilersAndroid", "android.jar")
      spec.withModule("intellij.android.profilersAndroid.gradle", "android.jar")
      spec.withModule("intellij.android.projectSystem.apk", "android.jar")
      spec.withModule("intellij.android.projectSystem.gradle.psd", "android.jar")
      spec.withModule("intellij.android.projectSystem.gradle.repositorySearch", "android.jar")
      spec.withModule("intellij.android.projectSystem.gradle.upgrade", "android.jar")
      spec.withModule("intellij.android.projectSystem.gradle", "android.jar")
      spec.withModule("intellij.android.projectSystem", "android.jar")
      spec.withModule("intellij.android.render-resources", "android.jar")
      spec.withModule("intellij.android.rendering", "android.jar")
      spec.withModule("intellij.android.room", "android.jar")
      //spec.withModule("intellij.android.samples-browser", "android.jar") AS Koala Merge
      spec.withModule("intellij.android.sdkUpdates", "android.jar")
      spec.withModule("intellij.android.threading-checker", "android.jar")
      spec.withModule("intellij.android.transport", "android.jar")
      spec.withModule("intellij.android.newProjectWizard", "android.jar")
      spec.withModule("intellij.android.wear-pairing", "android.jar")
      spec.withModule("intellij.android.wear-whs", "android.jar")
      spec.withModule("intellij.android.wizard.model", "android.jar")
      spec.withModule("intellij.android.wizard", "android.jar")
      spec.withModule("intellij.android.native-symbolizer", "android.jar")
      spec.withModule("intellij.android.native-symbolizer.gradle", "android.jar")
      //tools/adt/idea/whats-new-assistant:whats-new-assistant <= REMOVED
      spec.withModule("intellij.android.app-inspection.inspectors.network.ide", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.network.model", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.network.view", "android.jar")
      spec.withModule("intellij.android.server-flags", "android.jar")
      spec.withModule("intellij.android.codenavigation", "android.jar")
      spec.withModule("intellij.android.execution.common", "android.jar")
      spec.withModule("intellij.android.avd", "android.jar")

      spec.withModule("intellij.android.safemode", "android.jar")

      spec.withModule("intellij.android.preview-fast-compile", "android.jar")
      spec.withModule("intellij.android.completion", "android.jar")

      // artwork.jar
      spec.withModule("intellij.android.artwork", "artwork.jar")
      spec.withModule("intellij.android.artwork-compose", "artwork.jar")
      // build-common.jar
      spec.withModule("intellij.android.buildCommon", "build-common.jar")

      // inspectors-common.jar
      spec.withModule("intellij.android.inspectors-common.api", "inspectors-common.jar")
      spec.withModule("intellij.android.inspectors-common.api-ide", "inspectors-common.jar")
      spec.withModule("intellij.android.inspectors-common.ui", "inspectors-common.jar")

      // layoutlib-api.jar


      // layoutlib-loader.jar
      spec.withModule("intellij.android.layoutlib-loader", "layoutlib-loader.jar")

      // lint-ide.jar
      spec.withModule("intellij.android.lint.common", "lint-ide.jar")

      // manifest-merger.jar


      // memory-usage.jar
      spec.withModule("intellij.android.memory-usage", "memory-usage.jar")

      // utp.jar
      spec.withModule("intellij.android.utp", "utp.jar")

      // libs:
      spec.withModuleLibrary("jb-r8", "intellij.android.kotlin.idea.common", "")
      //prebuilts/tools/common/m2:eclipse-layout-kernel <= not recognized



      // We do not bundle Google Login API
      //spec.withModuleLibrary("javax-servlet", "google-login-as", "")
      //spec.withModuleLibrary("jsr305-2.0.1", "google-login-as", "")
      //spec.withModuleLibrary("oauth2", "google-login-as", "")


      // Module is disabled intellij.android.adt.ui.compose since no modules use it
      //spec.withModuleLibrary("compose-desktop-animation", "intellij.android.adt.ui.compose", "")
      //spec.withModuleLibrary("compose-desktop-foundation", "intellij.android.adt.ui.compose", "")
      //spec.withModuleLibrary("compose-desktop-material", "intellij.android.adt.ui.compose", "")
      //spec.withModuleLibrary("compose-desktop-runtime", "intellij.android.adt.ui.compose", "")
      //spec.withModuleLibrary("compose-desktop-ui", "intellij.android.adt.ui.compose", "")
      //spec.withModuleLibrary("skiko", "intellij.android.adt.ui.compose", "")

      spec.withProjectLibrary("asm-tools")

      val ffmpegVersion = "6.0-1.5.9"
      val javacppVersion = "1.5.9"

      // Add ffmpeg and javacpp
      spec.withModuleLibrary("ffmpeg", "intellij.android.streaming",  "ffmpeg-$ffmpegVersion.jar")
      spec.withModuleLibrary("ffmpeg-javacpp", "intellij.android.streaming", "javacpp-$javacppVersion.jar")

      // todo notarization
      spec.excludeModuleLibrary("ffmpeg-macos-aarch64", "intellij.android.streaming")
      spec.excludeModuleLibrary("ffmpeg-macos-x64", "intellij.android.streaming")
      spec.excludeModuleLibrary("javacpp-macos-aarch64", "intellij.android.streaming")
      spec.excludeModuleLibrary("javacpp-macos-x64", "intellij.android.streaming")

      // include only required as platform-dependent binaries
      for ((supportedOs, supportedArch) in supportedFfmpegPresets) {
        val osName = supportedOs.osName.lowercase(Locale.ENGLISH)
        val ffmpegLibraryName = "ffmpeg-$osName-$supportedArch"
        val javacppLibraryName = "javacpp-$osName-$supportedArch"

        if (allPlatforms) {
          // for the Marketplace we include all binaries
          spec.withModuleLibrary(ffmpegLibraryName, "intellij.android.streaming", "${ffmpegLibraryName}-$ffmpegVersion.jar")
          spec.withModuleLibrary(javacppLibraryName, "intellij.android.streaming", "${javacppLibraryName}-$javacppVersion.jar")
        }
        else {
          spec.withGeneratedPlatformResources(supportedOs, supportedArch) { targetDir, context ->
            val streamingModule = context.projectModel.project.modules.find { it.name == "intellij.android.streaming" }!!
            val ffmpegLibrary = streamingModule.libraryCollection.findLibrary(ffmpegLibraryName)!!
            val javacppLibrary = streamingModule.libraryCollection.findLibrary(javacppLibraryName)!!
            val libDir = targetDir.resolve("lib")

            copyFileToDir(ffmpegLibrary.getFiles(JpsOrderRootType.COMPILED)[0].toPath(), libDir)
            copyFileToDir(javacppLibrary.getFiles(JpsOrderRootType.COMPILED)[0].toPath(), libDir)
          }

          spec.excludeModuleLibrary(ffmpegLibraryName, "intellij.android.streaming")
          spec.excludeModuleLibrary(javacppLibraryName, "intellij.android.streaming")
        }
      }

      spec.withModule("intellij.android.streaming")

      // Library that aggregates all libraries and modules from `tools/base` directory
      spec.withProjectLibrary("studio-platform")

      // Used in intellij.android.app-quality-insights.api module which currently isn't bundled in IJ IDEA
      //spec.withProjectLibrary("gradle-shared-proto")
      //spec.withModuleLibrary("play_vitals_java_proto", "intellij.android.app-quality-insights.play-vitals.model", "")
      /**
       * TODO Check if needed since following modules reference it:
       * - intellij.android.app-inspection.inspectors.database
       * - intellij.android.app-inspection.inspectors.database.tests
       */
      //spec.withProjectLibrary("sqlite-inspector-proto")
      // We do not bundle Google API client in IJ
      //spec.withProjectLibrary("google-api-client")
      spec.withProjectLibrary("aapt-proto")
      spec.withProjectLibrary("google-baksmali")
      spec.withProjectLibrary("google-dexlib2")
      //spec.withProjectLibrary("gradle-shared-proto")
      spec.withProjectLibrary("HdrHistogram")
      spec.withProjectLibrary("javax-inject")
      //spec.withProjectLibrary("jetty")
      spec.withProjectLibrary("kotlinx-coroutines-guava")
      spec.withProjectLibrary("kxml2")
      //spec.withProjectLibrary("libadb-server-proto")
      //spec.withProjectLibrary("oauth2")
      //spec.withModuleLibrary("libandroid-core-proto", "intellij.android.projectSystem.gradle", "")
      //tools/adt/idea/android/lib:android-sdk-tools-jps <= this is jarutils.jar
      spec.withModuleLibrary("instantapps-api", "intellij.android.core", "")
      //spec.withModuleLibrary("play_vitals_java_proto", "intellij.android.app-quality-insights.play-vitals.model", "")
      //tools/adt/idea/compose-designer:ui-animation-tooling-internal <= not recognized
      //tools/vendor/google/game-tools/main:game-tools-protos <= not recognized
      // :libs


      //"resources": [
      // contents of "/plugins/android/lib/layoutlib/" will be downloaded by the AndroidPlugin on demand
      // Profiler downloader will download all the other profiler libraries: profilers-transform.jar, perfa_okhttp.dex, perfa, perfd, simpleperf
      // Profiler downloader will also download instant run installers: /resources/installer
      // Profiler downloader will also download instant run transport: /resources/transport

      //  "//tools/adt/idea/android/lib:sample-data-bundle",
      spec.withResourceFromModule("intellij.android.core", "lib/sampleData", "resources/sampleData")
      // "//tools/adt/idea/android/lib:apks-bundle",
      spec.withResourceFromModule("intellij.android.core", "lib/apks", "resources/apks")
      //  "//tools/adt/idea/artwork:device-art-resources-bundle",  # duplicated in android.jar
      spec.withResourceFromModule("intellij.android.artwork", "resources/device-art-resources", "resources/device-art-resources")
      //  "//tools/adt/idea/android/annotations:androidAnnotations",
      spec.withResourceArchiveFromModule("intellij.android.plugin", "../android/annotations", "resources/androidAnnotations.jar")
      //  "//tools/adt/idea/emulator/native:native_lib",
      spec.withResourceFromModule("intellij.android.streaming", "native/linux", "resources/native/linux")
      spec.withResourceFromModule("intellij.android.streaming", "native/mac", "resources/native/mac")
      spec.withResourceFromModule("intellij.android.streaming", "native/mac_arm", "resources/native/mac_arm")
      spec.withResourceFromModule("intellij.android.streaming", "native/win", "resources/native/win")
      // "//tools/adt/idea/emulator/screen-sharing-agent:bundle", TODO-ank

      //  "//tools/base/app-inspection/inspectors/backgroundtask:bundle",
      //  "//tools/base/app-inspection/inspectors/network:bundle",
      //  "//tools/base/dynamic-layout-inspector/agent/appinspection:bundle",
      //  "tools/base/process-monitor/process-tracker-agent:bundle",
      //  "//tools/base/profiler/transform:profilers-transform",
      //  "//tools/base/profiler/app:perfa",
      //  "//tools/base/profiler/app:perfa_okhttp",
      //  "//tools/base/tracer:trace_agent.jar",  # TODO(b/149320690): remove in 4.1 final release.
      //"//tools/base/transport:transport-bundle",
      //"//prebuilts/tools:simpleperf-bundle",
      //"//prebuilts/tools/common/perfetto:perfetto-bundle",
      //"//prebuilts/tools/common/app-inspection/androidx/sqlite:sqlite-inspection-bundle",
      //"//tools/base/deploy/installer:android-installer-bundle",
      //"//tools/adt/idea/android:asset-studio-bundle",
      spec.withResourceFromModule("intellij.android.core", "resources/images/asset_studio", "resources/images/asset_studio")
      //"//prebuilts/tools/common/trace-processor-daemon:trace-processor-daemon-bundle",
      //],
      //
      // END OF BAZEL FILE

      // here go some differences from original Android Studio layout

      for (entry in additionalModulesToJars.entries) {
        spec.withModule(entry.key, entry.value)
      }

      addition?.invoke(spec)
    }

  fun javaFXPlugin(mainModuleName: String): PluginLayout {
    return pluginAutoWithDeprecatedCustomDirName(mainModuleName) { spec ->
      spec.directoryName = "javaFX"
      spec.mainJarName = "javaFX.jar"
      spec.withModule("intellij.javaFX.jps")
      spec.withModule("intellij.javaFX.common", "javaFX-common.jar")
      spec.withModule("intellij.javaFX.sceneBuilder", "rt/sceneBuilderBridge.jar")
    }
  }

  fun aeDatabasePlugin(mainModuleName: String, extraModules: List<String> = emptyList()): PluginLayout {
    return plugin(mainModuleName) { spec ->
      spec.directoryName = "ae-database"
      spec.mainJarName = "ae-database.jar"
      spec.withModules(listOf(
        "intellij.ae.database.core",
        "intellij.ae.database.counters.community"
      ))
      spec.bundlingRestrictions.includeInDistribution = PluginDistribution.ALL
      if (extraModules.isNotEmpty()) {
        spec.withModules(extraModules)
      }
    }
  }

  fun githubPlugin(mainModuleName: String, kind: String): PluginLayout {
    return plugin(mainModuleName) { spec ->
      spec.directoryName = "vcs-github-$kind"
      spec.mainJarName = "vcs-github.jar"
      spec.withModules(listOf(
        "intellij.vcs.github"
      ))
      spec.withCustomVersion { _, version, _ ->
        PluginVersionEvaluatorResult(pluginVersion = "$version-$kind")
      }
    }
  }

  fun groovyPlugin(additionalModules: List<String> = emptyList(), addition: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    return plugin("intellij.groovy") { spec ->
      spec.directoryName = "Groovy"
      spec.mainJarName = "Groovy.jar"
      spec.withModules(listOf(
        "intellij.groovy.psi",
        "intellij.groovy.structuralSearch",
        "intellij.groovy.git",
      ))
      spec.withModule("intellij.groovy.jps", "groovy-jps.jar")
      spec.withModule("intellij.groovy.rt", "groovy-rt.jar")
      spec.withModule("intellij.groovy.spock.rt", "groovy-spock-rt.jar")
      spec.withModule("intellij.groovy.rt.classLoader", "groovy-rt-class-loader.jar")
      spec.withModule("intellij.groovy.constants.rt", "groovy-constants-rt.jar")
      spec.withModules(additionalModules)

      spec.excludeFromModule("intellij.groovy.psi", "standardDsls/**")
      spec.withResource("groovy-psi/resources/standardDsls", "lib/standardDsls")
      spec.withResource("hotswap/gragent.jar", "lib/agent")
      spec.withResource("groovy-psi/resources/conf", "lib")
      addition?.invoke(spec)
    }
  }
}

private suspend fun copyAnt(pluginDir: Path, context: BuildContext): List<DistributionFileEntry> {
  val antDir = pluginDir.resolve("dist")
  return TraceManager.spanBuilder("copy Ant lib").setAttribute("antDir", antDir.toString()).useWithScope {
    val sources = ArrayList<ZipSource>()
    val libraryData = ProjectLibraryData(libraryName = "Ant", packMode = LibraryPackMode.MERGED, reason = "ant")
    copyDir(
      sourceDir = context.paths.communityHomeDir.resolve("lib/ant"),
      targetDir = antDir,
      dirFilter = { !it.endsWith("src") },
      fileFilter = { file ->
        if (file.toString().endsWith(".jar")) {
          sources.add(ZipSource(file = file, distributionFileEntryProducer = null))
          false
        }
        else {
          true
        }
      },
    )
    sources.sort()

    val antTargetFile = antDir.resolve("ant.jar")
    buildJar(targetFile = antTargetFile, sources = sources)

    sources.map { source ->
      ProjectLibraryEntry(
        path = antTargetFile,
        data = libraryData,
        libraryFile = source.file,
        hash = source.hash,
        size = source.size,
        relativeOutputFile = "dist/ant.jar",
      )
    }
  }
}
