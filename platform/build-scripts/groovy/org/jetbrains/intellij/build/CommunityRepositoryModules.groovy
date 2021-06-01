// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.python.PythonCommunityPluginModules
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Files

import static org.jetbrains.intellij.build.impl.PluginLayout.plugin

@CompileStatic
final class CommunityRepositoryModules {
  /**
   * Specifies non-trivial layout for all plugins which sources are located in 'community' and 'contrib' repositories
   */
  static List<PluginLayout> COMMUNITY_REPOSITORY_PLUGINS = [
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
      withProjectLibrary("Gradle")
    },
    plugin("intellij.packageSearch"),
    plugin("intellij.externalSystem.dependencyUpdater"),
    plugin("intellij.gradle.dependencyUpdater"),
    plugin("intellij.android.gradle.dsl"),
    plugin("intellij.gradle.java") {
      withModule("intellij.gradle.jps")
    },
    plugin("intellij.gradle.java.maven"),
    plugin("intellij.platform.testGuiFramework") {
      mainJarName = "testGuiFramework.jar"
      withProjectLibrary("fest")
      withProjectLibrary("fest-swing")
    },
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
    // required for android plugin
    plugin("intellij.android.smali") {
      withModule("intellij.android.smali")
    },
    plugin("intellij.completionMlRanking"),
    plugin("intellij.completionMlRankingModels") {
      bundlingRestrictions.includeInEapOnly = true
    },
    plugin("intellij.statsCollector") {
      bundlingRestrictions.includeInEapOnly = true
    },
    plugin("intellij.ml.models.local") {
      bundlingRestrictions.includeInEapOnly = true
    },
    plugin("intellij.jps.cache"),
    plugin("intellij.lombok") {
      withModule("intellij.lombok.generated")
    },
    plugin("intellij.android.jpsBuildPlugin") {
      withModule("intellij.android.jpsBuildPlugin.common")
      withModule("intellij.android.jpsBuildPlugin.jps", "jps/android-jps-plugin.jar")
    },
    plugin("intellij.grazie") {
      withModule("intellij.grazie.core")
      withModule("intellij.grazie.java")
      withModule("intellij.grazie.json")
      withModule("intellij.grazie.markdown")
      withModule("intellij.grazie.properties")
      withModule("intellij.grazie.xml")
      withModule("intellij.grazie.yaml")
    }
  ]

  static List<PluginLayout> CONTRIB_REPOSITORY_PLUGINS = [
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
  ]

  static PluginLayout androidPlugin(Map<String, String> additionalModulesToJars) {
    // the following is copied from https://android.googlesource.com/platform/tools/idea/+/studio-master-dev/build/groovy/org/jetbrains/intellij/build/AndroidStudioProperties.groovy
    plugin("intellij.android.plugin") {
      directoryName = "android"
      mainJarName = "android.jar"
      withCustomVersion({pluginXmlFile, ideVersion ->
        String text = Files.readString(pluginXmlFile)
        def declaredVersion = text.substring(text.indexOf("<version>") + "<version>".length(), text.indexOf("</version>"))
        return "$declaredVersion.$ideVersion"
      })

      withModule("intellij.android.common", "android-common.jar")
      withModule("intellij.android.buildCommon", "build-common.jar")

      withModule("intellij.android.core", "android.jar")
      withModule("intellij.android.adb", "android.jar")
      withModule("intellij.android.app-inspection", "android.jar")
      withModule("intellij.android.app-inspection.ide", "android.jar")
      withModule("intellij.android.app-inspection.inspector", "android.jar")
      withModule("intellij.android.app-inspection.inspector.ide", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.workmanager.ide", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.workmanager.model", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.workmanager.view", "android.jar")
      withModule("intellij.android.dagger", "android.jar")
      withModule("intellij.android.databinding", "android.jar")
      withModule("intellij.android.debuggers", "android.jar")
      withModule("intellij.android.emulator", "android.jar")
      //withModule("intellij.android.gradle.dsl", "android.jar") // this is in IJ platform currently
      withModule("intellij.android.lang", "android.jar")
      withModule("intellij.android.lang-databinding", "android.jar")
      withModule("intellij.android.mlkit", "android.jar")
      withModule("intellij.android.room", "android.jar")
      withModule("intellij.android.plugin", "android.jar")
      withModule("intellij.android.artwork")
      withModule("intellij.android.build-attribution", "android.jar")
      withModule("intellij.android.observable", "android.jar")
      withModule("intellij.android.observable.ui", "android.jar")
      withModule("android.sdktools.flags", "android.jar")
      withModule("intellij.android.layout-inspector", "android.jar")
      withModule("intellij.android.layout-ui", "android.jar")
      withModule("intellij.android.transport", "android.jar")
      withModule("intellij.android.designer", "android.jar")
      withModule("intellij.android.compose-designer", "android.jar")
      withModule("intellij.android.designer.customview", "android.jar")
      withModule("intellij.android.nav.editor", "android.jar")
      withModule("intellij.android.nav.safeargs", "android.jar")
      withModule("intellij.android.sdkUpdates", "android.jar")
      withModule("intellij.android.wizard", "android.jar")
      withModule("intellij.android.wizard.model", "android.jar")
      withModule("android.sdktools.wizardTemplate.plugin", "wizard-template.jar")
      withModule("android.sdktools.wizardTemplate.impl", "wizard-template.jar")
      withModule("intellij.android.profilersAndroid", "android.jar")
      withModule("intellij.android.deploy", "android.jar")
      withModule("intellij.android.kotlin.idea", "android-kotlin.jar")
      withModule("intellij.android.kotlin.idea.common", "android-kotlin.jar")
      withModule("intellij.android.kotlin.output.parser", "android-kotlin.jar")
      withModule("intellij.android.kotlin.extensions.common", "android-extensions-ide.jar")
      withModule("intellij.android.kotlin.extensions", "android-extensions-ide.jar")
      withModule("intellij.android.transportDatabase", "android-profilers.jar")
      withModule("intellij.android.profilers", "android-profilers.jar")
      withModule("intellij.android.profilers.ui", "android-profilers.jar")
      withModule("intellij.android.profilers.atrace", "android-profilers.jar")
      withModule("intellij.android.native-symbolizer", "android.jar")
      withModule("intellij.android.apkanalyzer", "android.jar")
      withModule("intellij.android.projectSystem", "android.jar")
      withModule("intellij.android.projectSystem.gradle", "android.jar")
      withModule("intellij.android.projectSystem.gradle.psd", "android.jar")
      withModule("intellij.android.projectSystem.gradle.sync", "android.jar")
      withModule("intellij.android.gradle-tooling", "android.jar")
      withModule("intellij.android.gradle-tooling.impl", "android.jar")
      //withModule("intellij.android.newProjectWizard", "android.jar") // exclude empty module from IDEA
      withModule("intellij.android.resources-base", "android.jar")
      withModule("intellij.android.testRetention", "android.jar")
      withModule("intellij.android.android-layout-inspector", "android.jar")
      /* do not put into IJ android plugin: analytics */
      /* do not put into IJ android plugin: assistant, connection-assistant, whats-new-assistant */
      withModule("intellij.android.lint", "lint-ide.jar")
      withModule("intellij.android.adt.ui", "adt-ui.jar")
      withModule("intellij.android.adt.ui.model", "adt-ui.jar")
      withModule("android.sdktools.repository")
      withModule("android.sdktools.db-baseLibrary", "data-binding.jar")
      withModule("android.sdktools.db-baseLibrarySupport", "data-binding.jar")
      withModule("android.sdktools.db-compilerCommon", "data-binding.jar")
      withModule("android.sdktools.db-compiler", "data-binding.jar")
      withModule("android.sdktools.sdklib", "sdklib.jar")
      withModule("android.sdktools.sdk-common", "sdk-common.jar")
      withModule("intellij.android.layoutlib-loader", "layoutlib-loader.jar")

      withModule("android.sdktools.layoutlib-api") // force layoutlib-standard (IDEA-256114)
      withModuleLibrary("layoutlib", "intellij.android.layoutlib", "")

      //withModule("android.game-tools.main", "game-tools.jar") // no such module in IDEA
      withModule("android.sdktools.manifest-merger", "manifest-merger.jar")
      withModule("android.sdktools.chunkio", "pixelprobe.jar")
      withModule("android.sdktools.pixelprobe", "pixelprobe.jar")

      withModule("android.sdktools.binary-resources", "sdk-tools.jar")
      withModule("android.sdktools.analyzer", "sdk-tools.jar")
      withModule("android.sdktools.ddmlib", "sdk-tools.jar")
      withModule("android.sdktools.dvlib", "sdk-tools.jar")
      withModule("android.sdktools.deployer", "sdk-tools.jar")
      withModule("android.sdktools.zipflinger", "sdk-tools.jar")
      withModule("android.sdktools.tracer", "sdk-tools.jar")
      withModule("android.sdktools.draw9patch", "sdk-tools.jar")
      withModule("android.sdktools.lint-api", "sdk-tools.jar")
      withModule("android.sdktools.lint-checks", "sdk-tools.jar")
      withModule("android.sdktools.lint-model", "sdk-tools.jar")
      withModule("android.sdktools.mlkit-common", "sdk-tools.jar")
      withModule("android.sdktools.ninepatch", "sdk-tools.jar")
      withModule("android.sdktools.perflib", "sdk-tools.jar")
      withModule("android.sdktools.builder-model", "sdk-tools.jar")
      withModule("android.sdktools.builder-test-api", "sdk-tools.jar")
      withModule("android.sdktools.android-annotations", "sdk-tools.jar")
      withModule("android.sdktools.layoutinspector", "sdk-tools.jar")
      withModule("android.sdktools.usb-devices", "sdk-tools.jar")

      withModule("intellij.android.jps.model")

      withProjectLibrary("kxml2") //todo[nik] move to module libraries

      withProjectLibrary("asm-tools")
      withResourceFromModule("intellij.android.core", "lib/commons-compress-1.8.1.jar", "lib")
      withResourceFromModule("intellij.android.core", "lib/javawriter-2.2.1.jar", "lib")

      withResourceFromModule("intellij.android.artwork", "resources/device-art-resources", "lib/device-art-resources")
      withResourceFromModule("intellij.android.core", "lib/sampleData", "lib/sampleData")
      withResourceArchive("../android/annotations", "lib/androidAnnotations.jar")

      // here go some differences from original Android Studio layout

      // contents of "/plugins/android/lib/layoutlib/" will be downloaded by the AndroidPlugin on demand

      // Android Studio project libraries that implicitly go to Android Studio platform libs
      withProjectLibrary("kotlinx-coroutines-guava")
      withProjectLibrary("sqlite-inspector-proto")
      withProjectLibrary("transport-proto")
      withProjectLibrary("perfetto-proto")
      withProjectLibrary("studio-proto")
      withProjectLibrary("studio-grpc")
      withProjectLibrary("layoutinspector-proto")
      withProjectLibrary("emulator-proto")

      // Asset Studio images.
      withResourceFromModule("intellij.android.core", "resources/images/asset_studio", "resources/images/asset_studio")

      // Profiler downloader will download all the other profiler libraries: profilers-transform.jar, perfa_okhttp.dex, perfa, perfd, simpleperf
      // Profiler downloader will also download instant run installers: /resources/installer
      // Profiler downloader will also download instant run transport: /resources/transport

      withModuleLibrary("precompiled-common", "android.sdktools.common", "")
      withModuleLibrary("precompiled-android-annotations", "android.sdktools.android-annotations", "")
      withModuleLibrary("precompiled-analytics-crash", "android.sdktools.analytics-crash", "")
      withModuleLibrary("precompiled-analytics-shared", "android.sdktools.analytics-shared", "")
      withModuleLibrary("precompiled-analytics-tracker", "android.sdktools.analytics-tracker", "")
      // FIXME-ank: add "analytics-publisher"?

      additionalModulesToJars.entrySet().each {
        withModule(it.key, it.value)
      }

      // FIXME-ank: We abuse `withGeneratedResources`. There is no intention to generate any resources, instead we want to create empty
      // output compile directory for modules with no sources, but have module libraries. This is to leverage existing logic that collects
      // module runtime libraries, and to avoid validation error saying that the module output dir does not exist.
      withGeneratedResources(new ResourcesGenerator() {
        @Override
        File generateResources(BuildContext buildContext) {
          buildContext.project.modules.forEach {
            JpsModule module -> FileUtil.createDirectory(new File(buildContext.getModuleOutputPath(module)))
          }
          return null
        }
      }, "lib")
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
