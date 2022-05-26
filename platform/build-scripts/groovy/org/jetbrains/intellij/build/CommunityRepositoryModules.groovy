// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.ProjectLibraryData
import org.jetbrains.intellij.build.kotlin.KotlinPluginBuilder
import org.jetbrains.intellij.build.python.PythonCommunityPluginModules
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiConsumer

import static org.jetbrains.intellij.build.impl.PluginLayout.plugin

@CompileStatic
final class CommunityRepositoryModules {
  /**
   * Specifies non-trivial layout for all plugins which sources are located in 'community' and 'contrib' repositories
   */
  public final static List<PluginLayout> COMMUNITY_REPOSITORY_PLUGINS = List.of(
    plugin("intellij.ant") {
      mainJarName = "antIntegration.jar"
      withModule("intellij.ant.jps", "ant-jps.jar")
    },
    plugin("intellij.laf.macos") {
      bundlingRestrictions.supportedOs = [OsFamily.MACOS]
    },
    plugin("intellij.webp"){
      withResource("lib/libwebp/linux", "lib/libwebp/linux")
      withResource("lib/libwebp/mac", "lib/libwebp/mac")
      withResource("lib/libwebp/win", "lib/libwebp/win")
    },
    plugin("intellij.laf.win10") {
      bundlingRestrictions.supportedOs = [OsFamily.WINDOWS]
    },
    plugin("intellij.java.guiForms.designer") {
      directoryName = "uiDesigner"
      mainJarName = "uiDesigner.jar"
      withModule("intellij.java.guiForms.jps", "jps/java-guiForms-jps.jar")
    },
    KotlinPluginBuilder.kotlinPlugin(),
    plugin("intellij.properties") {
      withModule("intellij.properties.psi", "properties.jar")
      withModule("intellij.properties.psi.impl", "properties.jar")
    },
    plugin("intellij.properties.resource.bundle.editor"),
    plugin("intellij.vcs.git") {
      withModule("intellij.vcs.git.rt", "git4idea-rt.jar")
    },
    plugin("intellij.vcs.svn"){
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
    plugin("intellij.platform.tracing.ide"),
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
      withResource("maven36-server-impl/lib/maven3", "lib/maven3")
      withResource("maven3-server-common/lib", "lib/maven3-server-lib")
      [
        "archetype-common-2.0-alpha-4-SNAPSHOT.jar",
        "commons-beanutils.jar",
        "maven-dependency-tree-1.2.jar",
        "mercury-artifact-1.0-alpha-6.jar",
        "nexus-indexer-1.2.3.jar"
      ].each {withResource("maven2-server-impl/lib/$it", "lib/maven2-server-lib")}
      doNotCopyModuleLibrariesAutomatically([
        "intellij.maven.server.m2.impl", "intellij.maven.server.m3.common", "intellij.maven.server.m36.impl", "intellij.maven.server.m3.impl", "intellij.maven.server.m30.impl",
        "intellij.maven.server.m2.impl", "intellij.maven.server.m36.impl", "intellij.maven.server.m3.impl", "intellij.maven.server.m30.impl",
        "intellij.maven.artifactResolver.common", "intellij.maven.artifactResolver.m2", "intellij.maven.artifactResolver.m3", "intellij.maven.artifactResolver.m31"
      ])
    },
    plugin("intellij.gradle") {
      withModule("intellij.gradle.common")
      withModule("intellij.gradle.toolingExtension", "gradle-tooling-extension-api.jar")
      withModule("intellij.gradle.toolingExtension.impl", "gradle-tooling-extension-impl.jar")
      withModule("intellij.gradle.toolingProxy")
      withProjectLibrary("Gradle", ProjectLibraryData.PackMode.STANDALONE_SEPARATE)
    },
    plugin("intellij.packageSearch") {
      withModule("intellij.packageSearch.gradle")
      withModule("intellij.packageSearch.maven")
      withModule("intellij.packageSearch.kotlin")
    },
    plugin("intellij.externalSystem.dependencyUpdater"),
    plugin("intellij.gradle.dependencyUpdater"),
    plugin("intellij.android.gradle.dsl"),
    plugin("intellij.gradle.java") {
      withModule("intellij.gradle.jps")
    },
    plugin("intellij.gradle.java.maven"),
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
    plugin("intellij.devkit") {
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
      bundlingRestrictions.supportedOs = [OsFamily.LINUX]
    },
    plugin("intellij.textmate") {
      withModule("intellij.textmate.core")
      withResource("lib/bundles", "lib/bundles")
    },
    PythonCommunityPluginModules.pythonCommunityPluginLayout(),
    plugin("intellij.android.smali"),
    plugin("intellij.completionMlRanking"),
    plugin("intellij.completionMlRankingModels") {
      bundlingRestrictions.includeInEapOnly = true
    },
    plugin("intellij.statsCollector") {
      bundlingRestrictions.includeInEapOnly = true
    },
    plugin("intellij.ml.models.local") {
      withModule("intellij.ml.models.local.java")
      bundlingRestrictions.includeInEapOnly = true
    },
    plugin("intellij.jps.cache"),
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
    plugin("intellij.java.rareRefactorings"),
    plugin("intellij.toml") {
      withModule("intellij.toml.core")
      withModule("intellij.toml.json")
    },
    plugin("intellij.markdown") {
      withModule("intellij.markdown.core")
      withModule("intellij.markdown.fenceInjection")
    }
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
    plugin("intellij.gauge"),
    plugin("intellij.protoeditor") {
      withModule("intellij.protoeditor.core")
      withModule("intellij.protoeditor.go")
      withModule("intellij.protoeditor.jvm")
      withModule("intellij.protoeditor.python")
    }
  )

  static PluginLayout androidPlugin(Map<String, String> additionalModulesToJars,
                                    String mainModuleName = "intellij.android.plugin",
                                    @DelegatesTo(PluginLayout.PluginLayoutSpec) Closure addition = {}) {
    // the following is adapted from https://android.googlesource.com/platform/tools/adt/idea/+/refs/heads/studio-main/studio/BUILD
    plugin(mainModuleName) {
      directoryName = "android"
      mainJarName = "android.jar"
      withCustomVersion({pluginXmlFile, ideVersion, _ ->
        String text = Files.readString(pluginXmlFile)
        String version = ideVersion;

        if (text.indexOf("<version>") != -1) {
          def declaredVersion = text.substring(text.indexOf("<version>") + "<version>".length(), text.indexOf("</version>"))
          version = "$declaredVersion.$ideVersion".toString()
        }

        return version
      })

      withModule("intellij.android.adt.ui", "adt-ui.jar")
      withModule("intellij.android.adt.ui.model", "adt-ui.jar")

      withModule("intellij.android.common", "android-common.jar")

      withModule("intellij.android.kotlin.extensions.common", "android-extensions-ide.jar")
      withModule("intellij.android.kotlin.extensions", "android-extensions-ide.jar")

      withModule("intellij.android.kotlin.idea", "android-kotlin.jar")
      withModule("intellij.android.kotlin.idea.common", "android-kotlin.jar")
      withModule("intellij.android.kotlin.output.parser", "android-kotlin.jar")

      withModule("intellij.android.profilers.atrace", "android-profilers.jar")
      withModule("intellij.android.profilers.ui", "android-profilers.jar")
      withModule("intellij.android.profilers", "android-profilers.jar")
      withModule("intellij.android.transportDatabase", "android-profilers.jar")

      // do not add tools/adt/idea/analytics:analytics
      withModule("intellij.android.android-layout-inspector", "android.jar")
      withModule("android.sdktools.flags", "android.jar")
      // do not add tools/adt/idea/assistant:assistant
      // do not add tools/adt/idea/connection-assistant:connection-assistant
      withModule("intellij.android.adb", "android.jar")
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
      withModule("intellij.android.compose-designer", "android.jar")
      withModule("intellij.android.compose-common", "android.jar")
      withModule("intellij.android.core", "android.jar")
      withModule("intellij.android.dagger", "android.jar")
      withModule("intellij.android.databinding", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.database", "android.jar")
      withModule("intellij.android.debuggers", "android.jar")
      withModule("intellij.android.deploy", "android.jar")
      withModule("intellij.android.designer.customview", "android.jar")
      withModule("intellij.android.designer", "android.jar")
      withModule("intellij.android.emulator", "android.jar")
      withModule("intellij.android.gradle-tooling.api", "android.jar")
      withModule("intellij.android.gradle-tooling.impl", "android.jar")
      //tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl // this is in IJ platform currently
      withModule("intellij.android.lang-databinding", "android.jar")
      withModule("intellij.android.lang", "android.jar")
      withModule("intellij.android.layout-inspector", "android.jar")
      withModule("intellij.android.layout-ui", "android.jar")
      withModule("intellij.android.mlkit", "android.jar")
      withModule("intellij.android.nav.editor", "android.jar")
      withModule("intellij.android.nav.safeargs", "android.jar")
      //withModule("intellij.android.newProjectWizard", "android.jar") // exclude empty module from IDEA
      withModule("intellij.android.observable.ui", "android.jar")
      withModule("intellij.android.observable", "android.jar")
      withModule("intellij.android.profilersAndroid", "android.jar")
      withModule("intellij.android.projectSystem.gradle.models", "android.jar")
      withModule("intellij.android.projectSystem.gradle.psd", "android.jar")
      withModule("intellij.android.projectSystem.gradle.repositorySearch", "android.jar")
      withModule("intellij.android.projectSystem.gradle.sync", "android.jar")
      withModule("intellij.android.projectSystem.gradle", "android.jar")
      withModule("intellij.android.projectSystem", "android.jar")
      withModule("intellij.android.resources-base", "android.jar")
      withModule("intellij.android.room", "android.jar")
      withModule("intellij.android.sdkUpdates", "android.jar")
      withModule("intellij.android.testRetention", "android.jar")
      withModule("intellij.android.transport", "android.jar")
      withModule("intellij.android.wizard.model", "android.jar")
      withModule("intellij.android.wizard", "android.jar")
      withModule("intellij.android.native-symbolizer", "android.jar")
      //tools/adt/idea/whats-new-assistant:whats-new-assistant
      withModule("android.sdktools.dynamic-layout-inspector.common", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.network.ide", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.network.model", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.network.view", "android.jar")
      withModule("intellij.android.server-flags", "android.jar")

      withModule("intellij.android.artwork")

      withModule("android.sdktools.repository")

      withModule("intellij.android.buildCommon", "build-common.jar")

      // "data-binding.jar": [
      withModule("android.sdktools.db-baseLibrary", "data-binding.jar")
      withModule("android.sdktools.db-baseLibrarySupport", "data-binding.jar")
      withModule("android.sdktools.db-compiler", "data-binding.jar")
      withModule("android.sdktools.db-compilerCommon", "data-binding.jar")
      //],

      //"game-tools.jar": [
      //    "//tools/vendor/google/game-tools/main:android.game-tools.main",
      //],

      //"inspectors-common.jar": [
      withModule("intellij.android.inspectors-common.api", "inspectors-common.jar")
      withModule("intellij.android.inspectors-common.api-ide", "inspectors-common.jar")
      withModule("intellij.android.inspectors-common.ui", "inspectors-common.jar")
      //],

      //"layoutlib-loader.jar": [
      withModule("intellij.android.layoutlib-loader", "layoutlib-loader.jar")
      //],

      //"lint-ide.jar": [
      withModule("intellij.android.lint", "lint-ide.jar")
      //],

      //"manifest-merger.jar": [
      withModule("android.sdktools.manifest-merger", "manifest-merger.jar")
      //],

      //"pixelprobe.jar": [
      withModule("android.sdktools.chunkio", "pixelprobe.jar")
      withModule("android.sdktools.pixelprobe", "pixelprobe.jar")
      //],

      //"sdk-common.jar": [
      withModule("android.sdktools.sdk-common", "sdk-common.jar")
      //],

      //"sdk-tools.jar": [
      withModule("android.sdktools.analyzer", "sdk-tools.jar")
      withModule("android.sdktools.android-annotations", "sdk-tools.jar")
      withModule("android.sdktools.binary-resources", "sdk-tools.jar")
      withModule("android.sdktools.builder-model", "sdk-tools.jar")
      withModule("android.sdktools.builder-test-api", "sdk-tools.jar")
      withModule("android.sdktools.ddmlib", "sdk-tools.jar")
      withModule("android.sdktools.deployer", "sdk-tools.jar")
      withModule("android.sdktools.draw9patch", "sdk-tools.jar")
      withModule("android.sdktools.dvlib", "sdk-tools.jar")
      withModule("android.sdktools.layoutinspector", "sdk-tools.jar")
      withModule("android.sdktools.lint-api", "sdk-tools.jar")
      withModule("android.sdktools.lint-checks", "sdk-tools.jar")
      withModule("android.sdktools.lint-model", "sdk-tools.jar")
      withModule("android.sdktools.mlkit-common", "sdk-tools.jar")
      withModule("android.sdktools.ninepatch", "sdk-tools.jar")
      withModule("android.sdktools.perflib", "sdk-tools.jar")
      withModule("android.sdktools.tracer", "sdk-tools.jar")
      withModule("android.sdktools.zipflinger", "sdk-tools.jar")
      withModule("android.sdktools.usb-devices", "sdk-tools.jar")
      //],

      //"sdklib.jar": [
      withModule("android.sdktools.sdklib", "sdklib.jar")
      //],

      //"utp.jar": [
      withModule("intellij.android.utp", "utp.jar")
      //],

      //"wizard-template.jar": [
      withModule("android.sdktools.wizardTemplate.impl", "wizard-template.jar")
      withModule("android.sdktools.wizardTemplate.plugin", "wizard-template.jar")
      //],

      //"google-analytics-library.jar": [
      withModuleLibrary("precompiled-analytics-shared", "android.sdktools.analytics-shared", "")
      withModuleLibrary("precompiled-analytics-tracker", "android.sdktools.analytics-tracker", "")
      //tools/analytics-library/publisher:analytics-publisher",
      withModuleLibrary("precompiled-analytics-crash", "android.sdktools.analytics-crash", "")
      //],

      //"android-base-common.jar": [
      withModule("android.sdktools.common", "android-base-common.jar")
      //],

      // libs = [
      withProjectLibrary("kotlinx-coroutines-guava")
      withProjectLibrary("aapt-proto")
      withProjectLibrary("aia-proto")
      withProjectLibrary("android-test-plugin-host-device-info-proto")
      withProjectLibrary("asm-tools")
      withProjectLibrary("baksmali")
      withProjectLibrary("dexlib2")
      withProjectLibrary("emulator-proto")
      withProjectLibrary("javax-inject")
      withProjectLibrary("layoutinspector-compose-proto")
      withProjectLibrary("layoutinspector-skia-proto")
      withProjectLibrary("layoutinspector-view-proto")
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
      //tools/adt/idea/android/lib:android-sdk-tools-jps
      //tools/adt/idea/app-inspection/inspectors/backgroundtask/view:background-inspector-proto
      //tools/adt/idea/app-inspection/inspectors/workmanager/view:workmanager-inspector-proto
      //tools/adt/idea/profilers:traceprocessor-proto
      //tools/vendor/google/game-tools/main:game-tools-protos
      // ]

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
      withModule("intellij.android.jps.model")
      withModule("android.sdktools.layoutlib-api") // force layoutlib-standard (IDEA-256114)
      withProjectLibrary("layoutlib")
      withProjectLibrary("kxml2")

      //these project-level libraries are used from Android plugin only, so it's better to include them into its lib directory
      withProjectLibrary("kotlin-gradle-plugin-model")
      withProjectLibrary("HdrHistogram")

      for (Map.Entry<String, String> entry in additionalModulesToJars.entrySet()) {
        withModule(entry.key, entry.value)
      }

      // FIXME-ank: We abuse `withGeneratedResources`. There is no intention to generate any resources, instead we want to create empty
      // output compile directory for modules with no sources, but have module libraries. This is to leverage existing logic that collects
      // module runtime libraries, and to avoid validation error saying that the module output dir does not exist.
      withGeneratedResources(new BiConsumer<Path, BuildContext>() {
        @Override
        void accept(Path targetDir, BuildContext context) {
          for (JpsModule module in context.project.modules) {
            Files.createDirectories(context.getModuleOutputDir(module))
          }
        }
      })

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
