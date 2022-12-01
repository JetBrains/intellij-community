// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.impl.BundledMavenDownloader
import org.jetbrains.intellij.build.impl.LibraryPackMode
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.PluginLayout.Companion.plugin
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.kotlin.KotlinPluginBuilder
import org.jetbrains.intellij.build.python.PythonCommunityPluginModules

import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiConsumer

object CommunityRepositoryModules {
  /**
   * Specifies non-trivial layout for all plugins which sources are located in 'community' and 'contrib' repositories
   */
  @Suppress("SpellCheckingInspection")
  val COMMUNITY_REPOSITORY_PLUGINS: PersistentList<PluginLayout> = persistentListOf(
    plugin("intellij.ant") { spec ->
      spec.mainJarName = "antIntegration.jar"
      spec.withModule("intellij.ant.jps", "ant-jps.jar")
    },
    plugin("intellij.laf.macos") { spec ->
      spec.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.MACOS)
    },
    plugin("intellij.webp") { spec ->
      spec.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.LINUX)
      spec.withResource("lib/libwebp/linux", "lib/libwebp/linux")
    },
    plugin("intellij.webp") { spec ->
      spec.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.MACOS)
      spec.withResource("lib/libwebp/mac", "lib/libwebp/mac")
    },
    plugin("intellij.webp") { spec ->
      spec.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.WINDOWS)
      spec.withResource("lib/libwebp/win", "lib/libwebp/win")
    },
    plugin("intellij.webp") { spec ->
      spec.bundlingRestrictions.ephemeral = true
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
    plugin("intellij.properties") { spec ->
      spec.withModule("intellij.properties.psi", "properties.jar")
      spec.withModule("intellij.properties.psi.impl", "properties.jar")
    },
    plugin("intellij.vcs.git") { spec ->
      spec.withModule("intellij.vcs.git.rt", "git4idea-rt.jar")
    },
    plugin("intellij.vcs.svn") { spec ->
      spec.withProjectLibrary("sqlite")
    },
    plugin("intellij.xpath") { spec ->
      spec.withModule("intellij.xpath.rt", "rt/xslt-rt.jar")
    },
    plugin("intellij.platform.langInjection") { spec ->
      spec.withModule("intellij.java.langInjection", "IntelliLang.jar")
      spec.withModule("intellij.xml.langInjection", "IntelliLang.jar")
      spec.withModule("intellij.java.langInjection.jps")
    },
    plugin("intellij.tasks.core") { spec ->
      spec.directoryName = "tasks"
      spec.withModule("intellij.tasks")
      spec.withModule("intellij.tasks.compatibility")
      spec.withModule("intellij.tasks.jira")
      spec.withModule("intellij.tasks.java")
    },
    plugin("intellij.xslt.debugger") { spec ->
      spec.withModule("intellij.xslt.debugger.rt", "xslt-debugger-rt.jar")
      spec.withModule("intellij.xslt.debugger.impl.rt", "rt/xslt-debugger-impl-rt.jar")
      spec.withModuleLibrary("Saxon-6.5.5", "intellij.xslt.debugger.impl.rt", "rt/saxon.jar")
      spec.withModuleLibrary("Saxon-9HE", "intellij.xslt.debugger.impl.rt", "rt/saxon9he.jar")
      spec.withModuleLibrary("Xalan-2.7.2", "intellij.xslt.debugger.impl.rt", "rt/xalan-2.7.2.jar")
      spec.withModuleLibrary("RMI Stubs", "intellij.xslt.debugger.rt", "rmi-stubs.jar")
    },
    plugin("intellij.maven") { spec ->
      spec.withModule("intellij.maven.jps")
      spec.withModule("intellij.maven.server.m3.common", "maven3-server-common.jar")
      spec.withModule("intellij.maven.server.m30.impl", "maven30-server.jar")
      spec.withModule("intellij.maven.server.m3.impl", "maven3-server.jar")
      spec.withModule("intellij.maven.server.m36.impl", "maven36-server.jar")
      spec.withModule("intellij.maven.errorProne.compiler")
      spec.withModule("intellij.maven.server.indexer", "maven-server-indexer.jar")
      /*

       */
      spec.withModuleLibrary(libraryName = "lucene-core:ind-deps", moduleName = "intellij.maven.server.indexer",
                             relativeOutputPath = "intellij.maven.server.indexer/lib")
      spec.withModuleLibrary(libraryName = "lucene-query-parser:ind-deps", moduleName = "intellij.maven.server.indexer",
                             relativeOutputPath = "intellij.maven.server.indexer/lib")
      spec.withModuleLibrary(libraryName = "lucene-backward-codecs:ind-deps", moduleName = "intellij.maven.server.indexer",
                             relativeOutputPath = "intellij.maven.server.indexer/lib")
      spec.withModuleLibrary(libraryName = "lucene-highlighter:ind-deps", moduleName = "intellij.maven.server.indexer",
                             relativeOutputPath = "intellij.maven.server.indexer/lib")
      spec.withModuleLibrary(libraryName = "lucene-analyzer:ind-deps", moduleName = "intellij.maven.server.indexer",
                             relativeOutputPath = "intellij.maven.server.indexer/lib")
      spec.withModuleLibrary(libraryName = "maven-resolver-util:ind-deps", moduleName = "intellij.maven.server.indexer",
                             relativeOutputPath = "intellij.maven.server.indexer/lib")
      spec.withModuleLibrary(libraryName = "apache.maven.indexer.core:6.2.2", moduleName = "intellij.maven.server.indexer",
                             relativeOutputPath = "intellij.maven.server.indexer/lib")
      spec.withModuleLibrary(libraryName = "apache.maven.core:3.8.3", moduleName = "intellij.maven.server.indexer",
                             relativeOutputPath = "intellij.maven.server.indexer/lib")
      spec.withModuleLibrary(libraryName = "apache.maven.wagon.provider.api:3.5.2", moduleName = "intellij.maven.server.indexer",
                             relativeOutputPath = "intellij.maven.server.indexer/lib")
      spec.withModuleLibrary(libraryName = "apache.maven.archetype.common:3.2.1", moduleName = "intellij.maven.server.indexer",
                             relativeOutputPath = "intellij.maven.server.indexer/lib")


      spec.withModule("intellij.maven.artifactResolver.m3", "artifact-resolver-m3.jar")
      spec.withModule("intellij.maven.artifactResolver.common", "artifact-resolver-m3.jar")

      spec.withModule("intellij.maven.artifactResolver.m31", "artifact-resolver-m31.jar")
      spec.withModule("intellij.maven.artifactResolver.common", "artifact-resolver-m31.jar")

      spec.withArtifact("maven-event-listener", "")
      spec.doNotCopyModuleLibrariesAutomatically(listOf(
        "intellij.maven.server.m3.common", "intellij.maven.server.m36.impl", "intellij.maven.server.m3.impl",
        "intellij.maven.server.m30.impl",
        "intellij.maven.server.m36.impl", "intellij.maven.server.m3.impl", "intellij.maven.server.m30.impl",
        "intellij.maven.artifactResolver.common", "intellij.maven.artifactResolver.m3", "intellij.maven.artifactResolver.m31",
        "intellij.maven.server.indexer"
      ))
      spec.withGeneratedResources(BiConsumer { targetDir, context ->
        val targetLib = targetDir.resolve("lib")

        val mavenLibs = BundledMavenDownloader.downloadMavenCommonLibs(context.paths.communityHomeDirRoot)
        copyDir(mavenLibs, targetLib.resolve("maven3-server-lib"))

        val mavenDist = BundledMavenDownloader.downloadMavenDistribution(context.paths.communityHomeDirRoot)
        copyDir(mavenDist, targetLib.resolve("maven3"))
      })
    },
    plugin(listOf(
      "intellij.gradle",
      "intellij.gradle.common",
      "intellij.gradle.toolingProxy",
    )) { spec ->
      spec.withModule("intellij.gradle.toolingExtension", "gradle-tooling-extension-api.jar")
      spec.withModule("intellij.gradle.toolingExtension.impl", "gradle-tooling-extension-impl.jar")
      spec.withProjectLibrary("Gradle", LibraryPackMode.STANDALONE_SEPARATE)
    },
    plugin(listOf(
      "intellij.packageSearch",
      "intellij.packageSearch.gradle",
      "intellij.packageSearch.maven",
      "intellij.packageSearch.kotlin",
      )) { spec ->
      spec.withModule("intellij.packageSearch.gradle.tooling", "pkgs-tooling-extension.jar")
      spec.withProjectLibrary("kotlinx-serialization-protobuf")
      spec.withProjectLibrary("package-search-version-utils")
    },
    plugin("intellij.android.gradle.dsl") { spec ->
      spec.withModule("intellij.android.gradle.dsl.kotlin")
      spec.withModule("intellij.android.gradle.dsl.toml")
    },
    plugin(listOf("intellij.gradle.java", "intellij.gradle.jps")),
    plugin("intellij.junit") { spec ->
      spec.mainJarName = "idea-junit.jar"
      spec.withModule("intellij.junit.rt", "junit-rt.jar")
      spec.withModule("intellij.junit.v5.rt", "junit5-rt.jar")
    },
    plugin("intellij.java.byteCodeViewer") { spec ->
      spec.mainJarName = "byteCodeViewer.jar"
    },
    plugin("intellij.testng") { spec ->
      spec.mainJarName = "testng-plugin.jar"
      spec.withModule("intellij.testng.rt", "testng-rt.jar")
      spec.withProjectLibrary("TestNG")
    },
    plugin(listOf("intellij.dev", "intellij.dev.psiViewer")),
    plugin("intellij.devkit") { spec ->
      spec.withModule("intellij.devkit.core")
      spec.withModule("intellij.devkit.git")
      spec.withModule("intellij.devkit.themes")
      spec.withModule("intellij.devkit.gradle")
      spec.withModule("intellij.devkit.i18n")
      spec.withModule("intellij.devkit.images")
      spec.withModule("intellij.devkit.intelliLang")
      spec.withModule("intellij.devkit.uiDesigner")
      spec.withModule("intellij.devkit.workspaceModel")
      spec.withModule("intellij.platform.workspaceModel.codegen")
      spec.withModule("intellij.java.devkit")
      spec.withModule("intellij.groovy.devkit")
      spec.withModule("intellij.devkit.jps")
    },
    plugin("intellij.eclipse") { spec ->
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
    plugin("intellij.terminal") { spec ->
      spec.withResource("resources/.zshenv", "")
      spec.withResource("resources/jediterm-bash.in", "")
      spec.withResource("resources/fish/config.fish", "fish")
    },
    plugin("intellij.emojipicker") { spec ->
      spec.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.LINUX)
    },
    plugin("intellij.textmate") { spec ->
      spec.withModule("intellij.textmate.core")
      spec.withResource("lib/bundles", "lib/bundles")
    },
    PythonCommunityPluginModules.pythonCommunityPluginLayout(),
    androidDesignPlugin(),
    plugin("intellij.completionMlRankingModels") { spec ->
      spec.bundlingRestrictions.includeInEapOnly = true
    },
    plugin("intellij.statsCollector") { spec ->
      spec.bundlingRestrictions.includeInEapOnly = true
    },
    plugin("intellij.fullLine") { spec ->
      spec.bundlingRestrictions.includeInEapOnly = true
      spec.withModules(listOf(
        "intellij.fullLine.core",
        "intellij.fullLine.local",
        "intellij.fullLine.python",
        "intellij.fullLine.java",
        "intellij.fullLine.kotlin"
      ))
    },
    plugin(listOf("intellij.lombok", "intellij.lombok.generated")),
    plugin(listOf(
      "intellij.grazie",
      "intellij.grazie.core",
      "intellij.grazie.java",
      "intellij.grazie.json",
      "intellij.grazie.markdown",
      "intellij.grazie.properties",
      "intellij.grazie.xml",
      "intellij.grazie.yaml",
    )),
    plugin(listOf("intellij.toml", "intellij.toml.core", "intellij.toml.json")),
    plugin(listOf(
      "intellij.markdown",
      "intellij.markdown.core",
      "intellij.markdown.fenceInjection",
      "intellij.markdown.frontmatter",
      "intellij.markdown.frontmatter.yaml",
      "intellij.markdown.frontmatter.toml",
    )),
    plugin(listOf("intellij.settingsSync", "intellij.settingsSync.git")),
    plugin(listOf(
      "intellij.sh",
      "intellij.sh.core",
      "intellij.sh.terminal",
      "intellij.sh.copyright",
      "intellij.sh.python",
      "intellij.sh.markdown",
    )),
    plugin("intellij.featuresTrainer") { spec ->
      spec.withModule("intellij.vcs.git.featuresTrainer")
      spec.withProjectLibrary("assertJ")
      spec.withProjectLibrary("assertj-swing")
      spec.withProjectLibrary("git-learning-project")
    },
    plugin(listOf(
      "intellij.searchEverywhereMl",
      "intellij.searchEverywhereMl.yaml",
      "intellij.searchEverywhereMl.vcs",
    )),
    plugin("intellij.platform.testFramework.ui") { spec ->
      spec.withModuleLibrary("intellij.remoterobot.remote.fixtures", spec.mainModule, "")
      spec.withModuleLibrary("intellij.remoterobot.robot.server.core", spec.mainModule, "")
      spec.withProjectLibrary("okhttp")
    },
  )

  @Suppress("SpellCheckingInspection")
  val CONTRIB_REPOSITORY_PLUGINS: PersistentList<PluginLayout> = persistentListOf(
    plugin("intellij.errorProne") { spec ->
      spec.withModule("intellij.errorProne.jps", "jps/errorProne-jps.jar")
    },
    plugin("intellij.cucumber.java") { spec ->
      spec.withModule("intellij.cucumber.jvmFormatter", "cucumber-jvmFormatter.jar")
      spec.withModule("intellij.cucumber.jvmFormatter3", "cucumber-jvmFormatter3.jar")
      spec.withModule("intellij.cucumber.jvmFormatter4", "cucumber-jvmFormatter4.jar")
      spec.withModule("intellij.cucumber.jvmFormatter5", "cucumber-jvmFormatter5.jar")
    },
    plugin("intellij.protoeditor") { spec ->
      spec.withModule("intellij.protoeditor.core")
      spec.withModule("intellij.protoeditor.go")
      spec.withModule("intellij.protoeditor.jvm")
      spec.withModule("intellij.protoeditor.python")
    },
    plugin("intellij.serial.monitor") { spec ->
      spec.withProjectLibrary("io.github.java.native.jssc", LibraryPackMode.STANDALONE_SEPARATE)
    }
  )

  private fun androidDesignPlugin(mainModuleName: String = "intellij.android.design-plugin"): PluginLayout {
    return plugin(mainModuleName) { spec ->
      spec.directoryName = "design-tools"
      spec.mainJarName = "design-tools.jar"

      // modules:
      // design-tools.jar
      spec.withModule("intellij.android.compose-designer", "design-tools.jar")
      spec.withModule("intellij.android.design-plugin", "design-tools.jar")
      @Suppress("SpellCheckingInspection")
      spec.withModule("intellij.android.designer.customview", "design-tools.jar")
      spec.withModule("intellij.android.designer", "design-tools.jar")
      spec.withModule("intellij.android.glance-designer", "design-tools.jar")
      spec.withModule("intellij.android.layoutlib", "design-tools.jar")
      spec.withModule("intellij.android.nav.editor", "design-tools.jar")


      // libs:
      spec.withProjectLibrary("layoutlib")

      spec.withProjectLibrary("studio-analytics-proto") // This is to avoid this library leaking into project jars
      // :libs

      //"resources": [
      //  "//prebuilts/studio/layoutlib:layoutlib",
      //  "//tools/adt/idea/compose-designer:kotlin-compiler-daemon-libs",
      //],
    }
  }

  @Suppress("SpellCheckingInspection")
  fun androidPlugin(additionalModulesToJars: Map<String, String> = emptyMap(),
                    mainModuleName: String = "intellij.android.plugin",
                    addition: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    // the following is adapted from https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/studio-main/studio/BUILD
    return plugin(mainModuleName) { spec ->
      spec.directoryName = "android"
      spec.mainJarName = "android.jar"
      spec.withCustomVersion(object : PluginLayout.VersionEvaluator {
        override fun evaluate(pluginXml: Path, ideBuildVersion: String, context: BuildContext): String {
          val text = Files.readString(pluginXml)
          if (text.indexOf("<version>") != -1) {
            val declaredVersion = text.substring(text.indexOf("<version>") + "<version>".length, text.indexOf("</version>"))
            return "$declaredVersion.$ideBuildVersion"
          }
          else {
            return ideBuildVersion
          }
        }
      })

      // modules:
      // adt-ui.jar
      spec.withModule("intellij.android.adt.ui.model", "adt-ui.jar")
      spec.withModule("intellij.android.adt.ui", "adt-ui.jar")

      // android-base-common.jar
      spec.withModuleLibrary("precompiled-common", "android.sdktools.common", "android-base-common.jar")

      // android-common.jar
      spec.withModule("intellij.android.common", "android-common.jar")
      spec.withModule("intellij.android.jps.model", "android-common.jar")

      // android-extensions-ide.jar
      spec.withModule("intellij.android.kotlin.extensions.common", "android-extensions-ide.jar") // <= ADDED
      spec.withModule("intellij.android.kotlin.extensions", "android-extensions-ide.jar")

      // android-kotlin-extensions-tooling.jar
      spec.withModule("intellij.android.kotlin.extensions.tooling", "android-kotlin-extensions-tooling.jar")

      // android-kotlin.jar
      spec.withModule("intellij.android.kotlin.idea", "android-kotlin.jar")
      spec.withModule("intellij.android.kotlin.output.parser", "android-kotlin.jar")

      // android-profilers.jar
      spec.withModule("intellij.android.profilers.atrace", "android-profilers.jar")
      spec.withModule("intellij.android.profilers.ui", "android-profilers.jar")
      spec.withModule("intellij.android.profilers", "android-profilers.jar")
      spec.withModule("intellij.android.transportDatabase", "android-profilers.jar")

      // android-rt.jar
      //tools/adt/idea/rt:intellij.android.rt <= REMOVED

      // android.jar
      spec.withModule("intellij.android.analytics", "android.jar")
      spec.withModuleLibrary("precompiled-flags", "android.sdktools.flags", "android.jar")
      spec.withModule("intellij.android.assistant", "android.jar")
      //tools/adt/idea/connection-assistant:connection-assistant <= REMOVED
      spec.withModule("intellij.android.adb", "android.jar")
      spec.withModule("intellij.android.adb.ui", "android.jar")
      spec.withModule("intellij.android.lint", "android.jar")
      spec.withModule("intellij.android.templates", "android.jar")
      spec.withModule("intellij.android.apkanalyzer", "android.jar")
      spec.withModule("intellij.android.app-inspection.api", "android.jar")
      spec.withModule("intellij.android.app-inspection.ide", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspector.api", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspector.ide", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.backgroundtask.ide", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.backgroundtask.model", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.backgroundtask.view", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.workmanager.ide", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.workmanager.model", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.workmanager.view", "android.jar")
      spec.withModule("intellij.android.build-attribution", "android.jar")
      spec.withModule("intellij.android.compose-common", "android.jar")
      spec.withModule("intellij.android.core", "android.jar")
      spec.withModule("intellij.android.navigator", "android.jar")
      spec.withModule("intellij.android.dagger", "android.jar")
      spec.withModule("intellij.android.databinding", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.database", "android.jar")
      spec.withModule("intellij.android.debuggers", "android.jar")
      spec.withModule("intellij.android.deploy", "android.jar")
      spec.withModule("intellij.android.device-explorer", "android.jar")
      spec.withModule("intellij.android.device-monitor", "android.jar")
      spec.withModule("intellij.android.device-manager", "android.jar")
      spec.withModule("intellij.android.emulator", "android.jar")
      //tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl <= REMOVED
      //tools/adt/idea/gradle-dsl-kotlin:intellij.android.gradle.dsl.kotlin <= REMOVED
      //spec.withModule("intellij.android.gradle.dsl.toml", "android.jar")  <= REMOVED
      spec.withModule("intellij.android.lang-databinding", "android.jar")
      spec.withModule("intellij.android.lang", "android.jar")
      spec.withModule("intellij.android.layout-inspector", "android.jar")
      spec.withModule("intellij.android.layout-ui", "android.jar")
      spec.withModule("intellij.android.logcat", "android.jar")
      spec.withModule("intellij.android.mlkit", "android.jar")
      spec.withModule("intellij.android.nav.safeargs", "android.jar")
      spec.withModule("intellij.android.newProjectWizard", "android.jar")
      spec.withModule("intellij.android.observable.ui", "android.jar")
      spec.withModule("intellij.android.observable", "android.jar")
      spec.withModule("intellij.android.plugin", "android.jar")
      spec.withModule("intellij.android.profilersAndroid", "android.jar")
      spec.withModule("intellij.android.projectSystem.gradle.models", "android.jar")
      spec.withModule("intellij.android.projectSystem.gradle.psd", "android.jar")
      spec.withModule("intellij.android.projectSystem.gradle.repositorySearch", "android.jar")
      spec.withModule("intellij.android.projectSystem.gradle.sync", "android.jar")
      spec.withModule("intellij.android.projectSystem.gradle.upgrade", "android.jar")
      spec.withModule("intellij.android.projectSystem.gradle", "android.jar")
      spec.withModule("intellij.android.projectSystem", "android.jar")
      spec.withModule("intellij.android.room", "android.jar")
      spec.withModule("intellij.android.sdkUpdates", "android.jar")
      spec.withModule("intellij.android.testRetention", "android.jar")
      spec.withModule("intellij.android.threading-checker", "android.jar")
      spec.withModule("intellij.android.transport", "android.jar")
      spec.withModule("intellij.android.wear-pairing", "android.jar")
      spec.withModule("intellij.android.wizard.model", "android.jar")
      spec.withModule("intellij.android.wizard", "android.jar")
      spec.withModule("intellij.android.native-symbolizer", "android.jar")
      //tools/adt/idea/whats-new-assistant:whats-new-assistant <= REMOVED
      spec.withModuleLibrary("precompiled-dynamic-layout-inspector.common", "android.sdktools.dynamic-layout-inspector.common",
                             "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.network.ide", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.network.model", "android.jar")
      spec.withModule("intellij.android.app-inspection.inspectors.network.view", "android.jar")
      spec.withModule("intellij.android.server-flags", "android.jar")
      spec.withModule("intellij.android.codenavigation", "android.jar")

      //android-gradle-tooling.jar
      spec.withModule("intellij.android.gradle-tooling.api", "android-gradle-tooling.jar")
      spec.withModule("intellij.android.gradle-tooling.impl", "android-gradle-tooling.jar")

      // artwork.jar
      spec.withModule("intellij.android.artwork", "artwork.jar")

      // build-common.jar
      spec.withModule("intellij.android.buildCommon", "build-common.jar")

      // data-binding.jar
      spec.withModuleLibrary("precompiled-db-baseLibrary", "android.sdktools.db-baseLibrary", "data-binding.jar")
      spec.withModuleLibrary("precompiled-db-baseLibrarySupport", "android.sdktools.db-baseLibrarySupport", "data-binding.jar")
      spec.withModuleLibrary("precompiled-db-compiler", "android.sdktools.db-compiler", "data-binding.jar")
      spec.withModuleLibrary("precompiled-db-compilerCommon", "android.sdktools.db-compilerCommon", "data-binding.jar")

      // game-tools.jar
      //tools/vendor/google/game-tools/main:android.game-tools.main <= REMOVED

      // google-analytics-library.jar
      spec.withModuleLibrary("precompiled-analytics-shared", "android.sdktools.analytics-shared", "google-analytics-library.jar")
      spec.withModuleLibrary("precompiled-analytics-tracker", "android.sdktools.analytics-tracker", "google-analytics-library.jar")
      //tools/analytics-library/publisher:analytics-publisher <= REMOVED
      spec.withModuleLibrary("precompiled-analytics-crash", "android.sdktools.analytics-crash", "google-analytics-library.jar")

      // inspectors-common.jar
      spec.withModule("intellij.android.inspectors-common.api", "inspectors-common.jar")
      spec.withModule("intellij.android.inspectors-common.api-ide", "inspectors-common.jar")
      spec.withModule("intellij.android.inspectors-common.ui", "inspectors-common.jar")

      // layoutlib-api.jar
      spec.withModuleLibrary("precompiled-layoutlib-api", "android.sdktools.layoutlib-api", "layoutlib-api.jar")

      // layoutlib-loader.jar
      spec.withModule("intellij.android.layoutlib-loader", "layoutlib-loader.jar")

      // lint-ide.jar
      spec.withModule("intellij.android.lint.common", "lint-ide.jar")

      // manifest-merger.jar
      spec.withModuleLibrary("precompiled-manifest-merger", "android.sdktools.manifest-merger", "manifest-merger.jar")

      // pixelprobe.jar
      spec.withModuleLibrary("precompiled-chunkio", "android.sdktools.chunkio", "pixelprobe.jar")
      spec.withModuleLibrary("precompiled-pixelprobe", "android.sdktools.pixelprobe", "pixelprobe.jar")

      // repository.jar
      spec.withModuleLibrary("precompiled-repository", "android.sdktools.repository", "repository.jar")

      // sdk-common.jar
      spec.withModuleLibrary("precompiled-sdk-common", "android.sdktools.sdk-common", "sdk-common.jar")

      // sdk-tools.jar
      spec.withModuleLibrary("precompiled-android-annotations", "android.sdktools.android-annotations", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-analyzer", "android.sdktools.analyzer", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-binary-resources", "android.sdktools.binary-resources", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-builder-model", "android.sdktools.builder-model", "sdk-tools.jar")
      //tools/base/build-system/builder-test-api:studio.android.sdktools.builder-test-api <= API for testing. Nice to have in IDEA.
      spec.withModuleLibrary("precompiled-adblib", "android.sdktools.adblib", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-adblib.ddmlibcompatibility", "android.sdktools.adblib.ddmlibcompatibility", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-adblib.tools", "android.sdktools.adblib.tools", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-ddmlib", "android.sdktools.ddmlib", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-device-provisioner", "android.sdktools.device-provisioner", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-deployer", "android.sdktools.deployer", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-dvlib", "android.sdktools.dvlib", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-draw9patch", "android.sdktools.draw9patch", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-lint-api", "android.sdktools.lint-api", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-lint-checks", "android.sdktools.lint-checks", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-lint-model", "android.sdktools.lint-model", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-manifest-parser", "android.sdktools.manifest-parser", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-mlkit-common", "android.sdktools.mlkit-common", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-ninepatch", "android.sdktools.ninepatch", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-perflib", "android.sdktools.perflib", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-resource-repository", "android.sdktools.resource-repository", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-threading-agent-callback", "android.sdktools.threading-agent-callback", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-tracer", "android.sdktools.tracer", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-usb-devices", "android.sdktools.usb-devices", "sdk-tools.jar")
      spec.withModuleLibrary("precompiled-zipflinger", "android.sdktools.zipflinger", "sdk-tools.jar")

      // sdklib.jar
      spec.withModuleLibrary("precompiled-sdklib", "android.sdktools.sdklib", "sdklib.jar")

      // utp.jar
      spec.withModule("intellij.android.utp", "utp.jar")

      // wizard-template.jar
      spec.withModuleLibrary("precompiled-wizardTemplate.impl", "android.sdktools.wizardTemplate.impl", "wizard-template.jar")
      spec.withModuleLibrary("precompiled-wizardTemplate.plugin", "android.sdktools.wizardTemplate.plugin", "wizard-template.jar")


      // libs:
      spec.withModuleLibrary("jb-r8", "intellij.android.kotlin.idea", "")
      spec.withModuleLibrary("explainer", "android.sdktools.analyzer", "")
      spec.withModuleLibrary("generator", "android.sdktools.analyzer", "")
      spec.withModuleLibrary("shared", "android.sdktools.analyzer", "")
      spec.withModuleLibrary("okio", "intellij.android.core", "")
      spec.withModuleLibrary("moshi", "intellij.android.core", "")
      //prebuilts/tools/common/m2:eclipse-layout-kernel <= not recognized
      spec.withModuleLibrary("juniversalchardet", "android.sdktools.db-compiler", "")
      spec.withModuleLibrary("commons-lang", "android.sdktools.db-compiler", "")
      spec.withModuleLibrary("javapoet", "android.sdktools.db-compiler", "")
      spec.withModuleLibrary("auto-common", "android.sdktools.db-compiler", "")
      spec.withModuleLibrary("jetifier-core", "android.sdktools.db-compilerCommon", "")
      spec.withModuleLibrary("flatbuffers-java", "android.sdktools.mlkit-common", "")
      spec.withModuleLibrary("tensorflow-lite-metadata", "android.sdktools.mlkit-common", "")
      spec.withModuleLibrary("trace-perfetto-library", "intellij.android.profilersAndroid", "")
      spec.withProjectLibrary("aapt-proto")
      spec.withProjectLibrary("aia-proto")
      spec.withProjectLibrary("android-test-plugin-host-device-info-proto")
      spec.withProjectLibrary("asm-tools")
      spec.withProjectLibrary("baksmali")
      spec.withProjectLibrary("dexlib2")
      spec.withProjectLibrary("emulator-proto")
      //tools/adt/idea/.idea/libraries:ffmpeg <= FIXME
      //spec.withProjectLibrary("firebase_java_proto") <= REMOVED
      spec.withProjectLibrary("HdrHistogram")
      spec.withProjectLibrary("javax-inject")
      spec.withProjectLibrary("kotlinx-coroutines-guava")
      spec.withProjectLibrary("kxml2")
      spec.withProjectLibrary("layoutinspector-skia-proto")
      spec.withProjectLibrary("layoutinspector-view-proto")
      spec.withProjectLibrary("libam-instrumentation-data-proto")
      spec.withProjectLibrary("libapp-processes-proto")
      spec.withProjectLibrary("network_inspector_java_proto")
      spec.withProjectLibrary("perfetto-proto")
      spec.withProjectLibrary("sqlite-inspector-proto")
      spec.withProjectLibrary("sqlite")
      spec.withProjectLibrary("studio-analytics-proto")
      spec.withProjectLibrary("studio-grpc")
      spec.withProjectLibrary("studio-proto")
      spec.withProjectLibrary("transport-proto")
      spec.withProjectLibrary("utp-core-proto-jarjar")
      spec.withProjectLibrary("zxing-core")
      spec.withModuleLibrary("libandroid-core-proto", "intellij.android.core", "")
      spec.withModuleLibrary("libstudio.android-test-plugin-host-retention-proto", "intellij.android.core", "")
      //tools/adt/idea/android/lib:android-sdk-tools-jps <= this is jarutils.jar
      spec.withModuleLibrary("instantapps-api", "intellij.android.core", "")
      spec.withModuleLibrary("spantable", "intellij.android.core", "")
      spec.withModuleLibrary("background-inspector-proto", "intellij.android.app-inspection.inspectors.backgroundtask.model", "")
      spec.withModuleLibrary("workmanager-inspector-proto", "intellij.android.app-inspection.inspectors.backgroundtask.model", "")
      spec.withModuleLibrary("background-inspector-proto", "intellij.android.app-inspection.inspectors.backgroundtask.view", "")
      spec.withModuleLibrary("workmanager-inspector-proto", "intellij.android.app-inspection.inspectors.backgroundtask.view", "")
      spec.withModuleLibrary("workmanager-inspector-proto", "intellij.android.app-inspection.inspectors.workmanager.model", "")
      spec.withModuleLibrary("workmanager-inspector-proto", "intellij.android.app-inspection.inspectors.workmanager.view", "")
      //tools/adt/idea/compose-designer:ui-animation-tooling-internal <= not recognized
      spec.withModuleLibrary("traceprocessor-proto", "intellij.android.profilersAndroid", "")
      spec.withModuleLibrary("traceprocessor-proto", "intellij.android.profilers", "")
      spec.withModuleLibrary("pepk", "intellij.android.projectSystem.gradle", "")
      spec.withModuleLibrary("libstudio.android-test-plugin-result-listener-gradle-proto", "intellij.android.utp", "")
      spec.withModuleLibrary("deploy_java_proto", "android.sdktools.deployer", "")
      spec.withModuleLibrary("libjava_sites", "android.sdktools.deployer", "")
      spec.withModuleLibrary("liblint-checks-proto", "android.sdktools.lint-checks", "")
      spec.withModuleLibrary("libjava_sites", "intellij.android.debuggers", "")
      spec.withModuleLibrary("libjava_version", "android.sdktools.deployer", "")
      //tools/vendor/google/game-tools/main:game-tools-protos <= not recognized
      spec.withModuleLibrary("compilerCommon.antlr_runtime.shaded", "android.sdktools.db-compiler", "")
      spec.withModuleLibrary("compilerCommon.antlr.shaded", "android.sdktools.db-compiler", "")
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
      spec.withResourceFromModule("intellij.android.emulator", "native/linux", "resources/native/linux")
      spec.withResourceFromModule("intellij.android.emulator", "native/mac", "resources/native/mac")
      spec.withResourceFromModule("intellij.android.emulator", "native/mac_arm", "resources/native/mac_arm")
      spec.withResourceFromModule("intellij.android.emulator", "native/win", "resources/native/win")
      // "//tools/adt/idea/emulator/screen-sharing-agent:bundle", TODO-ank

      //  "//tools/base/app-inspection/inspectors/backgroundtask:bundle",
      //  "//tools/base/app-inspection/inspectors/network:bundle",
      //  "//tools/base/dynamic-layout-inspector/agent/appinspection:bundle",
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

      //these project-level libraries are used from Android plugin only, so it's better to include them into its lib directory
      spec.withProjectLibrary("HdrHistogram")

      for (entry in additionalModulesToJars.entries) {
        spec.withModule(entry.key, entry.value)
      }

      addition?.invoke(spec)
    }
  }

  fun javaFXPlugin(mainModuleName: String): PluginLayout {
    return plugin(mainModuleName) { spec ->
      spec.directoryName = "javaFX"
      spec.mainJarName = "javaFX.jar"
      spec.withModule("intellij.javaFX")
      spec.withModule("intellij.javaFX.jps")
      spec.withModule("intellij.javaFX.common", "javaFX-common.jar")
      spec.withModule("intellij.javaFX.properties")
      spec.withModule("intellij.javaFX.sceneBuilder", "rt/sceneBuilderBridge.jar")
    }
  }

  @JvmStatic
  @JvmOverloads
  fun groovyPlugin(additionalModules: List<String> = emptyList(), addition: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    return plugin("intellij.groovy") { spec ->
      spec.directoryName = "Groovy"
      spec.mainJarName = "Groovy.jar"
      spec.withModules(listOf(
        "intellij.groovy.psi",
        "intellij.groovy.structuralSearch",
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