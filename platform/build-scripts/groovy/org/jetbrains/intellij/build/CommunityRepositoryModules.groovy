// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic
import kotlinx.collections.immutable.PersistentList
import org.jetbrains.intellij.build.impl.BundledMavenDownloader
import org.jetbrains.intellij.build.impl.LibraryPackMode
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.kotlin.KotlinPluginBuilder
import org.jetbrains.intellij.build.python.PythonCommunityPluginModules

import java.nio.file.Files
import java.nio.file.Path

import static kotlinx.collections.immutable.ExtensionsKt.persistentListOf
import static org.jetbrains.intellij.build.impl.PluginLayout.simplePlugin
import static org.jetbrains.intellij.build.impl.PluginLayoutGroovy.plugin

@CompileStatic
final class CommunityRepositoryModules {
  /**
   * Specifies non-trivial layout for all plugins which sources are located in 'community' and 'contrib' repositories
   */
  public final static PersistentList<PluginLayout> COMMUNITY_REPOSITORY_PLUGINS = persistentListOf(
    plugin("intellij.ant") {
      mainJarName = "antIntegration.jar"
      withModule("intellij.ant.jps", "ant-jps.jar")
    },
    plugin("intellij.laf.macos") {
      bundlingRestrictions.supportedOs = persistentListOf(OsFamily.MACOS)
    },
    plugin("intellij.webp") {
      withResource("lib/libwebp/linux", "lib/libwebp/linux")
      withResource("lib/libwebp/mac", "lib/libwebp/mac")
      withResource("lib/libwebp/win", "lib/libwebp/win")
    },
    plugin("intellij.laf.win10") {
      bundlingRestrictions.supportedOs = persistentListOf(OsFamily.WINDOWS)
    },
    plugin("intellij.java.guiForms.designer") {
      directoryName = "uiDesigner"
      mainJarName = "uiDesigner.jar"
      withModule("intellij.java.guiForms.jps", "jps/java-guiForms-jps.jar")
    },
    KotlinPluginBuilder.kotlinPlugin(KotlinPluginBuilder.KotlinUltimateSources.WITH_COMMUNITY_MODULES),
    plugin("intellij.properties") {
      withModule("intellij.properties.psi", "properties.jar")
      withModule("intellij.properties.psi.impl", "properties.jar")
    },
    simplePlugin("intellij.properties.resource.bundle.editor"),
    plugin("intellij.vcs.git") {
      withModule("intellij.vcs.git.rt", "git4idea-rt.jar")
    },
    plugin("intellij.vcs.svn") {
      withProjectLibrary("sqlite")
    },
    plugin("intellij.xpath") {
      withModule("intellij.xpath.rt", "rt/xslt-rt.jar")
    },
    plugin("intellij.platform.langInjection") {
      withModule("intellij.java.langInjection", "IntelliLang.jar")
      withModule("intellij.xml.langInjection", "IntelliLang.jar")
      withModule("intellij.java.langInjection.jps")
    },
    plugin("intellij.tasks.core") {
      directoryName = "tasks"
      withModule("intellij.tasks")
      withModule("intellij.tasks.compatibility")
      withModule("intellij.tasks.jira")
      withModule("intellij.tasks.java")
    },
    plugin("intellij.xslt.debugger") {
      withModule("intellij.xslt.debugger.rt", "xslt-debugger-rt.jar")
      withModule("intellij.xslt.debugger.impl.rt", "rt/xslt-debugger-impl-rt.jar")
      withModuleLibrary("Saxon-6.5.5", "intellij.xslt.debugger.impl.rt", "rt")
      withModuleLibrary("Saxon-9HE", "intellij.xslt.debugger.impl.rt", "rt")
      withModuleLibrary("Xalan-2.7.2", "intellij.xslt.debugger.impl.rt", "rt")
    },
    simplePlugin("intellij.platform.tracing.ide"),
    plugin("intellij.maven") {
      withModule("intellij.maven.jps")
      withModule("intellij.maven.server", "maven-server-api.jar")
      withModule("intellij.maven.server.m2.impl", "maven2-server.jar")
      withModule("intellij.maven.server.m3.common", "maven3-server-common.jar")
      withModule("intellij.maven.server.m30.impl", "maven30-server.jar")
      withModule("intellij.maven.server.m3.impl", "maven3-server.jar")
      withModule("intellij.maven.server.m36.impl", "maven36-server.jar")
      withModule("intellij.maven.errorProne.compiler")

      withModule("intellij.maven.artifactResolver.m2", "artifact-resolver-m2.jar")
      withModule("intellij.maven.artifactResolver.common", "artifact-resolver-m2.jar")

      withModule("intellij.maven.artifactResolver.m3", "artifact-resolver-m3.jar")
      withModule("intellij.maven.artifactResolver.common", "artifact-resolver-m3.jar")

      withModule("intellij.maven.artifactResolver.m31", "artifact-resolver-m31.jar")
      withModule("intellij.maven.artifactResolver.common", "artifact-resolver-m31.jar")

      withArtifact("maven-event-listener", "")
      [
        "archetype-common-2.0-alpha-4-SNAPSHOT.jar",
        "commons-beanutils.jar",
        "maven-dependency-tree-1.2.jar",
        "mercury-artifact-1.0-alpha-6.jar",
        "nexus-indexer-1.2.3.jar"
      ].each { withResource("maven2-server-impl/lib/$it", "lib/maven2-server-lib") }
      doNotCopyModuleLibrariesAutomatically([
        "intellij.maven.server.m2.impl", "intellij.maven.server.m3.common", "intellij.maven.server.m36.impl", "intellij.maven.server.m3.impl", "intellij.maven.server.m30.impl",
        "intellij.maven.server.m2.impl", "intellij.maven.server.m36.impl", "intellij.maven.server.m3.impl", "intellij.maven.server.m30.impl",
        "intellij.maven.artifactResolver.common", "intellij.maven.artifactResolver.m2", "intellij.maven.artifactResolver.m3", "intellij.maven.artifactResolver.m31"
      ])
      withGeneratedResources({ Path targetDir, BuildContext context ->
        Path targetLib = targetDir.resolve("lib")

        Path mavenLibs = BundledMavenDownloader.INSTANCE.downloadMavenCommonLibs(context.paths.communityHomeDir)
        FileUtil.copyDir(mavenLibs.toFile(), targetLib.resolve("maven3-server-lib").toFile())

        Path mavenDist = BundledMavenDownloader.INSTANCE.downloadMavenDistribution(context.paths.communityHomeDir)
        FileUtil.copyDir(mavenDist.toFile(), targetLib.resolve("maven3").toFile())
      })
    },
    plugin("intellij.gradle") {
      withModule("intellij.gradle.common")
      withModule("intellij.gradle.toolingExtension", "gradle-tooling-extension-api.jar")
      withModule("intellij.gradle.toolingExtension.impl", "gradle-tooling-extension-impl.jar")
      withModule("intellij.gradle.toolingProxy")
      withProjectLibrary("Gradle", LibraryPackMode.STANDALONE_SEPARATE)
    },
    plugin("intellij.packageSearch") {
      withModule("intellij.packageSearch.compat")
      withModule("intellij.packageSearch.gradle")
      withModule("intellij.packageSearch.maven")
      withModule("intellij.packageSearch.kotlin")
    },
    simplePlugin("intellij.gradle.dependencyUpdater"),
    plugin("intellij.android.gradle.dsl") {
      withModule("intellij.android.gradle.dsl.kotlin")
    },
    plugin("intellij.gradle.java") {
      withModule("intellij.gradle.jps")
    },
    simplePlugin("intellij.gradle.java.maven"),
    plugin("intellij.junit") {
      mainJarName = "idea-junit.jar"
      withModule("intellij.junit.rt", "junit-rt.jar")
      withModule("intellij.junit.v5.rt", "junit5-rt.jar")
    },
    plugin("intellij.java.byteCodeViewer") {
      mainJarName = "byteCodeViewer.jar"
    },
    plugin("intellij.testng") {
      mainJarName = "testng-plugin.jar"
      withModule("intellij.testng.rt", "testng-rt.jar")
      withProjectLibrary("TestNG")
    },
    plugin("intellij.dev") {
      withModule("intellij.dev.psiViewer")
    },
    plugin("intellij.devkit") {
      withModule("intellij.devkit.core")
      withModule("intellij.devkit.git")
      withModule("intellij.devkit.themes")
      withModule("intellij.devkit.gradle")
      withModule("intellij.devkit.i18n")
      withModule("intellij.devkit.images")
      withModule("intellij.devkit.intelliLang")
      withModule("intellij.devkit.uiDesigner")
      withModule("intellij.devkit.workspaceModel")
      withModule("intellij.platform.workspaceModel.codegen")
      withModule("intellij.java.devkit")
      withModule("intellij.groovy.devkit")
      withModule("intellij.devkit.jps")
    },
    plugin("intellij.eclipse") {
      withModule("intellij.eclipse.jps", "eclipse-jps.jar")
      withModule("intellij.eclipse.common", "eclipse-common.jar")
    },
    plugin("intellij.java.coverage") {
      withModule("intellij.java.coverage.rt")
      // explicitly pack JaCoCo as a separate JAR
      withModuleLibrary("JaCoCo", "intellij.java.coverage", "jacoco.jar")
    },
    plugin("intellij.java.decompiler") {
      directoryName = "java-decompiler"
      mainJarName = "java-decompiler.jar"
      withModule("intellij.java.decompiler.engine", mainJarName)
    },
    javaFXPlugin("intellij.javaFX.community"),
    plugin("intellij.terminal") {
      withResource("resources/.zshenv", "")
      withResource("resources/jediterm-bash.in", "")
      withResource("resources/fish/config.fish", "fish")
    },
    plugin("intellij.emojipicker") {
      bundlingRestrictions.supportedOs = persistentListOf(OsFamily.LINUX)
    },
    plugin("intellij.textmate") {
      withModule("intellij.textmate.core")
      withResource("lib/bundles", "lib/bundles")
    },
    PythonCommunityPluginModules.pythonCommunityPluginLayout(),
    simplePlugin("intellij.android.smali"),
    androidDesignPlugin(),
    simplePlugin("intellij.completionMlRanking"),
    plugin("intellij.completionMlRankingModels") {
      bundlingRestrictions.includeInEapOnly = true
    },
    plugin("intellij.statsCollector") {
      bundlingRestrictions.includeInEapOnly = true
    },
    plugin("intellij.lombok") {
      withModule("intellij.lombok.generated")
    },
    plugin("intellij.grazie") {
      withModule("intellij.grazie.core")
      withModule("intellij.grazie.java")
      withModule("intellij.grazie.json")
      withModule("intellij.grazie.markdown")
      withModule("intellij.grazie.properties")
      withModule("intellij.grazie.xml")
      withModule("intellij.grazie.yaml")
    },
    simplePlugin("intellij.java.rareRefactorings"),
    plugin("intellij.toml") {
      withModule("intellij.toml.core")
      withModule("intellij.toml.json")
    },
    plugin("intellij.markdown") {
      withModule("intellij.markdown.core")
      withModule("intellij.markdown.fenceInjection")
      withModule("intellij.markdown.frontmatter")
    },
    simplePlugin("intellij.platform.images"),
    simplePlugin("intellij.java.ide.customization"),
    simplePlugin("intellij.copyright"),
    simplePlugin("intellij.editorconfig"),
    simplePlugin("intellij.settingsRepository"),
    simplePlugin("intellij.settingsSync"),
    simplePlugin("intellij.configurationScript"),
    simplePlugin("intellij.yaml"),
    simplePlugin("intellij.repository.search"),
    simplePlugin("intellij.color.scheme.github"),
    simplePlugin("intellij.color.scheme.monokai"),
    simplePlugin("intellij.color.scheme.twilight"),
    simplePlugin("intellij.color.scheme.warmNeon"),
    simplePlugin("intellij.reStructuredText"),
    simplePlugin("intellij.maven.model"),
    simplePlugin("intellij.vcs.hg"),
    simplePlugin("intellij.vcs.github"),
    simplePlugin("intellij.java.i18n"),
    simplePlugin("intellij.java.debugger.streams"),
    simplePlugin("intellij.sh"),
    plugin("intellij.featuresTrainer") {
      withProjectLibrary("assertJ")
      withProjectLibrary("assertj-swing")
    },
    plugin("intellij.vcs.git.featuresTrainer") {
      withProjectLibrary("git-learning-project")
    },
    simplePlugin("intellij.searchEverywhereMl"),
    simplePlugin("intellij.keymap.eclipse"),
    simplePlugin("intellij.keymap.visualStudio"),
    simplePlugin("intellij.keymap.netbeans"),
    plugin("intellij.platform.testFramework.ui") {
      withModuleLibrary("intellij.remoterobot.ide.launcher", mainModule, "")
      withModuleLibrary("intellij.remoterobot.remote.fixtures", mainModule, "")
      withModuleLibrary("intellij.remoterobot.remote.robot", mainModule, "")
      withModuleLibrary("intellij.remoterobot.robot.server", mainModule, "")
      withProjectLibrary("okhttp")
    },
  )

  public final static List<PluginLayout> CONTRIB_REPOSITORY_PLUGINS = List.of(
    plugin("intellij.errorProne") {
      withModule("intellij.errorProne.jps", "jps/errorProne-jps.jar")
    },
    plugin("intellij.cucumber.java") {
      withModule("intellij.cucumber.jvmFormatter", "cucumber-jvmFormatter.jar")
      withModule("intellij.cucumber.jvmFormatter3", "cucumber-jvmFormatter3.jar")
      withModule("intellij.cucumber.jvmFormatter4", "cucumber-jvmFormatter4.jar")
      withModule("intellij.cucumber.jvmFormatter5", "cucumber-jvmFormatter5.jar")
    },
    plugin("intellij.cucumber.groovy") {
    },
    simplePlugin("intellij.gauge"),
    plugin("intellij.protoeditor") {
      withModule("intellij.protoeditor.core")
      withModule("intellij.protoeditor.go")
      withModule("intellij.protoeditor.jvm")
      withModule("intellij.protoeditor.python")
    },
    plugin("intellij.serial.monitor") {
      withProjectLibrary("io.github.java.native.jssc", LibraryPackMode.STANDALONE_SEPARATE)
    }
  )

  static PluginLayout androidDesignPlugin(String mainModuleName = "intellij.android.design-plugin") {
    plugin(mainModuleName) {
      directoryName = "design-tools"
      mainJarName = "design-tools.jar"

      // modules:
      // design-tools.jar
      withModule("intellij.android.compose-designer", "design-tools.jar")
      withModule("intellij.android.design-plugin", "design-tools.jar")
      withModule("intellij.android.designer.customview", "design-tools.jar")
      withModule("intellij.android.designer", "design-tools.jar")
      withModule("intellij.android.layoutlib", "design-tools.jar")
      withModule("intellij.android.nav.editor", "design-tools.jar")


      // libs:
      withProjectLibrary("layout_inspector_compose_java_proto") // <= ADDED
      withProjectLibrary("layout_inspector_snapshot_java_proto") // <= ADDED
      withProjectLibrary("layout_inspector_view_java_proto") // <= ADDED
      withProjectLibrary("layoutlib")
      // :libs

      //"resources": [
      //  "//prebuilts/studio/layoutlib:layoutlib",
      //  "//tools/adt/idea/compose-designer:kotlin-compiler-daemon-libs",
      //],
    }
  }

  static PluginLayout androidPlugin(Map<String, String> additionalModulesToJars,
                                    String mainModuleName = "intellij.android.plugin",
                                    @DelegatesTo(PluginLayout.PluginLayoutSpec) Closure addition = {}) {
    // the following is adapted from https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/studio-main/studio/BUILD
    plugin(mainModuleName) {
      directoryName = "android"
      mainJarName = "android.jar"
      withCustomVersion({pluginXmlFile, ideVersion, _ ->
        String text = Files.readString(pluginXmlFile)
        String version = ideVersion

        if (text.indexOf("<version>") != -1) {
          def declaredVersion = text.substring(text.indexOf("<version>") + "<version>".length(), text.indexOf("</version>"))
          version = "$declaredVersion.$ideVersion".toString()
        }

        return version
      })

      // modules:
      // adt-ui.jar
      withModule("intellij.android.adt.ui.model", "adt-ui.jar")
      withModule("intellij.android.adt.ui", "adt-ui.jar")

      // android-base-common.jar
      withModuleLibrary("precompiled-common", "android.sdktools.common", "android-base-common.jar")

      // android-common.jar
      withModule("intellij.android.common", "android-common.jar")

      // android-extensions-ide.jar
      withModule("intellij.android.kotlin.extensions.common", "android-extensions-ide.jar") // <= ADDED
      withModule("intellij.android.kotlin.extensions", "android-extensions-ide.jar")

      // android-kotlin.jar
      withModule("intellij.android.kotlin.idea", "android-kotlin.jar")
      withModule("intellij.android.kotlin.output.parser", "android-kotlin.jar")

      // android-profilers.jar
      withModule("intellij.android.profilers.atrace", "android-profilers.jar")
      withModule("intellij.android.profilers.ui", "android-profilers.jar")
      withModule("intellij.android.profilers", "android-profilers.jar")
      withModule("intellij.android.transportDatabase", "android-profilers.jar")

      // android-rt.jar
      //tools/adt/idea/rt:intellij.android.rt <= REMOVED

      // android.jar
      //tools/adt/idea/analytics:analytics <= REMOVED
      withModule("intellij.android.android-layout-inspector", "android.jar")
      withModuleLibrary("precompiled-flags", "android.sdktools.flags", "android.jar")
      withModule("intellij.android.assistant", "android.jar")
      //tools/adt/idea/connection-assistant:connection-assistant <= REMOVED
      withModule("intellij.android.adb", "android.jar")
      withModule("intellij.android.lint", "android.jar")
      withModule("intellij.android.templates", "android.jar")
      withModule("intellij.android.apkanalyzer", "android.jar")
      withModule("intellij.android.app-inspection.api", "android.jar")
      withModule("intellij.android.app-inspection.ide", "android.jar")
      withModule("intellij.android.app-inspection.inspector.api", "android.jar")
      withModule("intellij.android.app-inspection.inspector.ide", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.backgroundtask.ide", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.backgroundtask.model", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.backgroundtask.view", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.workmanager.ide", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.workmanager.model", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.workmanager.view", "android.jar")
      withModule("intellij.android.build-attribution", "android.jar")
      withModule("intellij.android.compose-common", "android.jar")
      withModule("intellij.android.core", "android.jar")
      withModule("intellij.android.navigator", "android.jar")
      withModule("intellij.android.dagger", "android.jar")
      withModule("intellij.android.databinding", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.database", "android.jar")
      withModule("intellij.android.debuggers", "android.jar")
      withModule("intellij.android.deploy", "android.jar")
      withModule("intellij.android.device-explorer", "android.jar")
      withModule("intellij.android.emulator", "android.jar")
      withModule("intellij.android.gradle-tooling.api", "android.jar")
      withModule("intellij.android.gradle-tooling.impl", "android.jar")
      //tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl <= REMOVED
      //tools/adt/idea/gradle-dsl-kotlin:intellij.android.gradle.dsl.kotlin <= REMOVED
      withModule("intellij.android.lang-databinding", "android.jar")
      withModule("intellij.android.lang", "android.jar")
      withModule("intellij.android.layout-inspector", "android.jar")
      withModule("intellij.android.layout-ui", "android.jar")
      withModule("intellij.android.logcat", "android.jar")
      withModule("intellij.android.mlkit", "android.jar")
      withModule("intellij.android.nav.safeargs", "android.jar")
      withModule("intellij.android.newProjectWizard", "android.jar")
      withModule("intellij.android.observable.ui", "android.jar")
      withModule("intellij.android.observable", "android.jar")
      withModule("intellij.android.plugin", "android.jar")
      withModule("intellij.android.profilersAndroid", "android.jar")
      withModule("intellij.android.projectSystem.gradle.models", "android.jar")
      withModule("intellij.android.projectSystem.gradle.psd", "android.jar")
      withModule("intellij.android.projectSystem.gradle.repositorySearch", "android.jar")
      withModule("intellij.android.projectSystem.gradle.sync", "android.jar")
      withModule("intellij.android.projectSystem.gradle", "android.jar")
      withModule("intellij.android.projectSystem", "android.jar")
      withModule("intellij.android.room", "android.jar")
      withModule("intellij.android.sdkUpdates", "android.jar")
      withModule("intellij.android.testRetention", "android.jar")
      withModule("intellij.android.transport", "android.jar")
      withModule("intellij.android.wizard.model", "android.jar")
      withModule("intellij.android.wizard", "android.jar")
      withModule("intellij.android.native-symbolizer", "android.jar")
      //tools/adt/idea/whats-new-assistant:whats-new-assistant <= REMOVED
      withModuleLibrary("precompiled-dynamic-layout-inspector.common", "android.sdktools.dynamic-layout-inspector.common", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.network.ide", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.network.model", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.network.view", "android.jar")
      withModule("intellij.android.server-flags", "android.jar")
      withModule("intellij.android.codenavigation", "android.jar")

      // artwork.jar
      withModule("intellij.android.artwork", "artwork.jar")

      // build-common.jar
      withModule("intellij.android.buildCommon", "build-common.jar")

      // data-binding.jar
      withModuleLibrary("precompiled-db-baseLibrary", "android.sdktools.db-baseLibrary", "data-binding.jar")
      withModuleLibrary("precompiled-db-baseLibrarySupport", "android.sdktools.db-baseLibrarySupport", "data-binding.jar")
      withModuleLibrary("precompiled-db-compiler", "android.sdktools.db-compiler", "data-binding.jar")
      withModuleLibrary("precompiled-db-compilerCommon", "android.sdktools.db-compilerCommon", "data-binding.jar")

      // game-tools.jar
      //tools/vendor/google/game-tools/main:android.game-tools.main <= REMOVED

      // google-analytics-library.jar
      withModuleLibrary("precompiled-analytics-shared", "android.sdktools.analytics-shared", "google-analytics-library.jar")
      withModuleLibrary("precompiled-analytics-tracker", "android.sdktools.analytics-tracker", "google-analytics-library.jar")
      //tools/analytics-library/publisher:analytics-publisher <= REMOVED
      withModuleLibrary("precompiled-analytics-crash", "android.sdktools.analytics-crash", "google-analytics-library.jar")

      // inspectors-common.jar
      withModule("intellij.android.inspectors-common.api", "inspectors-common.jar")
      withModule("intellij.android.inspectors-common.api-ide", "inspectors-common.jar")
      withModule("intellij.android.inspectors-common.ui", "inspectors-common.jar")

      // layoutlib-api.jar
      withModuleLibrary("precompiled-layoutlib-api", "android.sdktools.layoutlib-api", "layoutlib-api.jar")

      // layoutlib-loader.jar
      withModule("intellij.android.layoutlib-loader", "layoutlib-loader.jar")

      // lint-ide.jar
      withModule("intellij.android.lint.common", "lint-ide.jar")

      // manifest-merger.jar
      withModuleLibrary("precompiled-manifest-merger", "android.sdktools.manifest-merger", "manifest-merger.jar")

      // pixelprobe.jar
      withModuleLibrary("precompiled-chunkio", "android.sdktools.chunkio", "pixelprobe.jar")
      withModuleLibrary("precompiled-pixelprobe", "android.sdktools.pixelprobe", "pixelprobe.jar")

      // repository.jar
      withModuleLibrary("precompiled-repository", "android.sdktools.repository", "repository.jar")

      // sdk-common.jar
      withModuleLibrary("precompiled-sdk-common", "android.sdktools.sdk-common", "sdk-common.jar")

      // sdk-tools.jar
      withModuleLibrary("precompiled-android-annotations", "android.sdktools.android-annotations", "sdk-tools.jar")
      withModuleLibrary("precompiled-analyzer", "android.sdktools.analyzer", "sdk-tools.jar")
      withModuleLibrary("precompiled-binary-resources", "android.sdktools.binary-resources", "sdk-tools.jar")
      withModuleLibrary("precompiled-builder-model", "android.sdktools.builder-model", "sdk-tools.jar")
      //tools/base/build-system/builder-test-api:studio.android.sdktools.builder-test-api <= API for testing. Nice to have in IDEA.
      withModuleLibrary("precompiled-adblib", "android.sdktools.adblib", "sdk-tools.jar")
      withModuleLibrary("precompiled-ddmlib", "android.sdktools.ddmlib", "sdk-tools.jar")
      withModuleLibrary("precompiled-deployer", "android.sdktools.deployer", "sdk-tools.jar")
      withModuleLibrary("precompiled-dvlib", "android.sdktools.dvlib", "sdk-tools.jar")
      withModuleLibrary("precompiled-draw9patch", "android.sdktools.draw9patch", "sdk-tools.jar")
      withModuleLibrary("precompiled-layoutinspector", "android.sdktools.layoutinspector", "sdk-tools.jar")
      withModuleLibrary("precompiled-lint-api", "android.sdktools.lint-api", "sdk-tools.jar")
      withModuleLibrary("precompiled-lint-checks", "android.sdktools.lint-checks", "sdk-tools.jar")
      withModuleLibrary("precompiled-lint-model", "android.sdktools.lint-model", "sdk-tools.jar")
      withModuleLibrary("precompiled-manifest-parser", "android.sdktools.manifest-parser", "sdk-tools.jar")
      withModuleLibrary("precompiled-mlkit-common", "android.sdktools.mlkit-common", "sdk-tools.jar")
      withModuleLibrary("precompiled-ninepatch", "android.sdktools.ninepatch", "sdk-tools.jar")
      withModuleLibrary("precompiled-perflib", "android.sdktools.perflib", "sdk-tools.jar")
      withModuleLibrary("precompiled-resource-repository", "android.sdktools.resource-repository", "sdk-tools.jar")
      withModuleLibrary("precompiled-tracer", "android.sdktools.tracer", "sdk-tools.jar")
      withModuleLibrary("precompiled-usb-devices", "android.sdktools.usb-devices", "sdk-tools.jar")
      withModuleLibrary("precompiled-zipflinger", "android.sdktools.zipflinger", "sdk-tools.jar")

      // sdklib.jar
      withModuleLibrary("precompiled-sdklib", "android.sdktools.sdklib", "sdklib.jar")

      // utp.jar
      withModule("intellij.android.utp", "utp.jar")

      // wizard-template.jar
      withModuleLibrary("precompiled-wizardTemplate.impl", "android.sdktools.wizardTemplate.impl", "wizard-template.jar")
      withModuleLibrary("precompiled-wizardTemplate.plugin", "android.sdktools.wizardTemplate.plugin", "wizard-template.jar")


      // libs:
      withProjectLibrary("layout_inspector_compose_java_proto") // <= ADDED
      withProjectLibrary("layout_inspector_snapshot_java_proto") // <= ADDED
      withProjectLibrary("layout_inspector_view_java_proto") // <= ADDED
      withModuleLibrary("jb-r8", "intellij.android.kotlin.idea", "")
      withModuleLibrary("explainer", "android.sdktools.analyzer", "")
      withModuleLibrary("generator", "android.sdktools.analyzer", "")
      withModuleLibrary("shared", "android.sdktools.analyzer", "")
      withModuleLibrary("okio", "intellij.android.core", "")
      withModuleLibrary("moshi", "intellij.android.core", "")
      withModuleLibrary("utp-core-proto", "intellij.android.core", "")
      //prebuilts/tools/common/m2:eclipse-layout-kernel <= not recognized
      withModuleLibrary("juniversalchardet", "android.sdktools.db-compiler", "")
      withModuleLibrary("commons-lang", "android.sdktools.db-compiler", "")
      withModuleLibrary("javapoet", "android.sdktools.db-compiler", "")
      withModuleLibrary("auto-common", "android.sdktools.db-compiler", "")
      withModuleLibrary("jetifier-core", "android.sdktools.db-compilerCommon", "")
      withModuleLibrary("flatbuffers-java", "android.sdktools.mlkit-common", "")
      withModuleLibrary("tensorflow-lite-metadata", "android.sdktools.mlkit-common", "")
      withProjectLibrary("aapt-proto")
      withProjectLibrary("aia-proto")
      withProjectLibrary("android-test-plugin-host-device-info-proto")
      withProjectLibrary("asm-tools")
      withProjectLibrary("baksmali")
      withProjectLibrary("dexlib2")
      withProjectLibrary("emulator-proto")
      //tools/adt/idea/.idea/libraries:ffmpeg <= FIXME
      withProjectLibrary("javax-inject")
      withProjectLibrary("kotlinx-coroutines-guava")
      withProjectLibrary("kxml2")
      withProjectLibrary("layoutinspector-skia-proto")
      //tools/adt/idea/.idea/libraries:layoutinspector-view-proto <= replaced with 3 x layout_inspector_xxx_java_proto
      withProjectLibrary("libam-instrumentation-data-proto")
      withProjectLibrary("libapp-processes-proto")
      withProjectLibrary("network_inspector_java_proto")
      withProjectLibrary("perfetto-proto")
      withProjectLibrary("sqlite-inspector-proto")
      withProjectLibrary("sqlite")
      withProjectLibrary("studio-analytics-proto")
      withProjectLibrary("studio-grpc")
      withProjectLibrary("studio-proto")
      withProjectLibrary("transport-proto")
      withProjectLibrary("zxing-core")
      withModuleLibrary("libandroid-core-proto", "intellij.android.core", "")
      withModuleLibrary("libstudio.android-test-plugin-host-retention-proto", "intellij.android.core", "")
      //tools/adt/idea/android/lib:android-sdk-tools-jps <= this is jarutils.jar
      withModuleLibrary("instantapps-api", "intellij.android.core", "")
      withModuleLibrary("spantable", "intellij.android.core", "")
      withModuleLibrary("background-inspector-proto", "intellij.android.app-inspection.inspectors.backgroundtask.model", "")
      withModuleLibrary("workmanager-inspector-proto", "intellij.android.app-inspection.inspectors.backgroundtask.model", "")
      withModuleLibrary("background-inspector-proto", "intellij.android.app-inspection.inspectors.backgroundtask.view", "")
      withModuleLibrary("workmanager-inspector-proto", "intellij.android.app-inspection.inspectors.backgroundtask.view", "")
      withModuleLibrary("workmanager-inspector-proto", "intellij.android.app-inspection.inspectors.workmanager.model", "")
      withModuleLibrary("workmanager-inspector-proto", "intellij.android.app-inspection.inspectors.workmanager.view", "")
      //tools/adt/idea/compose-designer:ui-animation-tooling-internal <= not recognized
      withModuleLibrary("traceprocessor-proto", "intellij.android.profilersAndroid", "")
      withModuleLibrary("traceprocessor-proto", "intellij.android.profilers", "")
      withModuleLibrary("pepk", "intellij.android.projectSystem.gradle", "")
      withModuleLibrary("libstudio.android-test-plugin-result-listener-gradle-proto", "intellij.android.utp", "")
      withModuleLibrary("deploy_java_proto", "android.sdktools.deployer", "")
      withModuleLibrary("libjava_sites", "android.sdktools.deployer", "")
      withModuleLibrary("libjava_sites", "intellij.android.debuggers", "")
      withModuleLibrary("libjava_version", "android.sdktools.deployer", "")
      //tools/vendor/google/game-tools/main:game-tools-protos <= not recognized
      withModuleLibrary("compilerCommon.antlr_runtime.shaded", "android.sdktools.db-compiler", "")
      withModuleLibrary("compilerCommon.antlr.shaded", "android.sdktools.db-compiler", "")
      // :libs


      //"resources": [
      // contents of "/plugins/android/lib/layoutlib/" will be downloaded by the AndroidPlugin on demand
      // Profiler downloader will download all the other profiler libraries: profilers-transform.jar, perfa_okhttp.dex, perfa, perfd, simpleperf
      // Profiler downloader will also download instant run installers: /resources/installer
      // Profiler downloader will also download instant run transport: /resources/transport

      //  "//tools/adt/idea/android/lib:sample-data-bundle",
      withResourceFromModule("intellij.android.core", "lib/sampleData", "resources/sampleData")
      //  "//tools/adt/idea/artwork:device-art-resources-bundle",  # duplicated in android.jar
      withResourceFromModule("intellij.android.artwork", "resources/device-art-resources", "resources/device-art-resources")
      //  "//tools/adt/idea/android/annotations:androidAnnotations",
      withResourceArchiveFromModule("intellij.android.plugin", "../android/annotations", "resources/androidAnnotations.jar")
      //  "//tools/adt/idea/emulator/native:native_lib",
      withResourceFromModule("intellij.android.emulator", "native/linux", "resources/native/linux")
      withResourceFromModule("intellij.android.emulator", "native/mac", "resources/native/mac")
      withResourceFromModule("intellij.android.emulator", "native/mac_arm", "resources/native/mac_arm")
      withResourceFromModule("intellij.android.emulator", "native/win", "resources/native/win")
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
      withResourceFromModule("intellij.android.core", "resources/images/asset_studio", "resources/images/asset_studio")
      //"//prebuilts/tools/common/trace-processor-daemon:trace-processor-daemon-bundle",
      //],
      //
      // END OF BAZEL FILE

      // here go some differences from original Android Studio layout

      //these project-level libraries are used from Android plugin only, so it's better to include them into its lib directory
      withProjectLibrary("HdrHistogram")

      for (Map.Entry<String, String> entry in additionalModulesToJars.entrySet()) {
        withModule(entry.key, entry.value)
      }

      addition.delegate = delegate
      addition()
    }
  }

  static PluginLayout javaFXPlugin(String mainModuleName) {
    plugin(mainModuleName) {
      directoryName = "javaFX"
      mainJarName = "javaFX.jar"
      withModule("intellij.javaFX", mainJarName)
      withModule("intellij.javaFX.jps")
      withModule("intellij.javaFX.common", "javaFX-common.jar")
      withModule("intellij.javaFX.properties")
      withModule("intellij.javaFX.sceneBuilder", "rt/sceneBuilderBridge.jar")
    }
  }

  static PluginLayout groovyPlugin(List<String> additionalModules, @DelegatesTo(PluginLayout.PluginLayoutSpec) Closure addition = {}) {
    plugin("intellij.groovy") {
      directoryName = "Groovy"
      mainJarName = "Groovy.jar"
      withModule("intellij.groovy.psi", mainJarName)
      withModule("intellij.groovy.structuralSearch", mainJarName)
      excludeFromModule("intellij.groovy.psi", "standardDsls/**")
      withModule("intellij.groovy.jps", "groovy-jps.jar")
      withModule("intellij.groovy.rt", "groovy-rt.jar")
      withModule("intellij.groovy.spock.rt", "groovy-spock-rt.jar")
      withModule("intellij.groovy.rt.classLoader", "groovy-rt-class-loader.jar")
      withModule("intellij.groovy.constants.rt", "groovy-constants-rt.jar")
      withResource("groovy-psi/resources/standardDsls", "lib/standardDsls")
      withResource("hotswap/gragent.jar", "lib/agent")
      withResource("groovy-psi/resources/conf", "lib")
      additionalModules.each {
        withModule(it)
      }
      addition.delegate = delegate
      addition()
    }
  }
}
