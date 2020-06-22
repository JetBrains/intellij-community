// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.python.PythonCommunityPluginModules
import org.jetbrains.jps.model.library.JpsOrderRootType

import static org.jetbrains.intellij.build.impl.PluginLayout.plugin

@CompileStatic
class CommunityRepositoryModules {
  /**
   * List of modules which are included into lib/platform-api.jar in all IntelliJ based IDEs. Build scripts of IDEs aren't supposed to use this
   * property directly, it's used by the build scripts internally.
   */
  @ApiStatus.Internal
  static List<String> PLATFORM_API_MODULES = [
    "intellij.platform.analysis",
    "intellij.platform.builtInServer",
    "intellij.platform.core",
    "intellij.platform.diff",
    "intellij.platform.vcs.dvcs",
    "intellij.platform.editor",
    "intellij.platform.externalSystem",
    "intellij.platform.indexing",
    "intellij.platform.jps.model",
    "intellij.platform.lang",
    "intellij.platform.lvcs",
    "intellij.platform.ide",
    "intellij.platform.projectModel",
    "intellij.platform.remoteServers.agent.rt",
    "intellij.platform.remoteServers",
    "intellij.platform.tasks",
    "intellij.platform.usageView",
    "intellij.platform.vcs.core",
    "intellij.platform.vcs",
    "intellij.platform.vcs.log",
    "intellij.platform.vcs.log.graph",
    "intellij.platform.debugger",
    "intellij.xml.analysis",
    "intellij.xml",
    "intellij.xml.psi",
    "intellij.xml.structureView",
  ]

  /**
   * List of modules which are included into lib/platform-impl.jar in all IntelliJ based IDEs. Build scripts of IDEs aren't supposed to use this
   * property directly, it's used by the build scripts internally.
   */
  @ApiStatus.Internal
  static List<String> PLATFORM_IMPLEMENTATION_MODULES = [
    "intellij.platform.analysis.impl",
    "intellij.platform.builtInServer.impl",
    "intellij.platform.core.impl",
    "intellij.platform.diff.impl",
    "intellij.platform.editor.ex",
    "intellij.platform.indexing.impl",
    "intellij.platform.execution.impl",
    "intellij.platform.inspect",
    "intellij.platform.lang.impl",
    "intellij.platform.workspaceModel.storage",
    "intellij.platform.workspaceModel.ide",
    "intellij.platform.lvcs.impl",
    "intellij.platform.ide.impl",
    "intellij.platform.projectModel.impl",
    "intellij.platform.externalSystem.impl",
    "intellij.platform.scriptDebugger.protocolReaderRuntime",
    "intellij.regexp",
    "intellij.platform.remoteServers.impl",
    "intellij.platform.scriptDebugger.backend",
    "intellij.platform.scriptDebugger.ui",
    "intellij.platform.smRunner",
    "intellij.platform.structureView.impl",
    "intellij.platform.tasks.impl",
    "intellij.platform.testRunner",
    "intellij.platform.debugger.impl"
  ]

  /**
   * Specifies non-trivial layout for all plugins which sources are located in 'community' and 'contrib' repositories
   */
  static List<PluginLayout> COMMUNITY_REPOSITORY_PLUGINS = [
    plugin("intellij.ant") {
      mainJarName = "antIntegration.jar"
      withModule("intellij.ant.jps")
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
      withModule("intellij.java.guiForms.jps", "jps/ui-designer-jps-plugin.jar")
    },
    plugin("intellij.properties") {
      withModule("intellij.properties.psi", "properties.jar")
      withModule("intellij.properties.psi.impl", "properties.jar")
    },
    plugin("intellij.properties.resource.bundle.editor"),
    plugin("intellij.vcs.git") {
      withModule("intellij.vcs.git.rt", "git4idea-rt.jar", null)
    },
    plugin("intellij.vcs.cvs") {
      directoryName = "cvsIntegration"
      mainJarName = "cvsIntegration.jar"
      withModule("intellij.vcs.cvs.javacvs")
      withModule("intellij.vcs.cvs.smartcvs")
      withModule("intellij.vcs.cvs.core", "cvs_util.jar")
    },
    plugin("intellij.xpath") {
      withModule("intellij.xpath.rt", "rt/xslt-rt.jar")
    },
    plugin("intellij.platform.langInjection") {
      withModule("intellij.java.langInjection", "IntelliLang.jar")
      withModule("intellij.xml.langInjection", "IntelliLang.jar")
      withModule("intellij.java.langInjection.jps", "intellilang-jps-plugin.jar")
      doNotCreateSeparateJarForLocalizableResources()
    },
    plugin("intellij.tasks.core") {
      directoryName = "tasks"
      withModule("intellij.tasks")
      withModule("intellij.tasks.compatibility")
      withModule("intellij.tasks.jira")
      withModule("intellij.tasks.java")
      doNotCreateSeparateJarForLocalizableResources()
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
      withModule("intellij.maven.server")
      withModule("intellij.maven.server.m2.impl")
      withModule("intellij.maven.server.m3.common")
      withModule("intellij.maven.server.m30.impl")
      withModule("intellij.maven.server.m3.impl")
      withModule("intellij.maven.server.m36.impl")
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
      withModule("intellij.gradle.toolingExtension")
      withModule("intellij.gradle.toolingExtension.impl")
      withModule("intellij.gradle.toolingLoaderRt")
      withProjectLibrary("Gradle")
    },
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
      withModule("intellij.eclipse.jps", "eclipse-jps-plugin.jar", null)
      withModule("intellij.eclipse.common")
    },
    plugin("intellij.java.coverage") {
      withModule("intellij.java.coverage.rt")
      withProjectLibrary("JaCoCo") //todo[nik] convert to module library
    },
    plugin("intellij.errorProne") {
      withModule("intellij.errorProne.jps", "jps/error-prone-jps-plugin.jar")
    },
    plugin("intellij.cucumber.java") {
      withModule("intellij.cucumber.jvmFormatter")
      withModule("intellij.cucumber.jvmFormatter3")
      withModule("intellij.cucumber.jvmFormatter4")
      withModule("intellij.cucumber.jvmFormatter5")
      doNotCreateSeparateJarForLocalizableResources()
    },
    plugin("intellij.cucumber.groovy") {
      doNotCreateSeparateJarForLocalizableResources()
    },
    plugin("intellij.java.decompiler") {
      directoryName = "java-decompiler"
      mainJarName = "java-decompiler.jar"
      withModule("intellij.java.decompiler.engine", mainJarName)
      doNotCreateSeparateJarForLocalizableResources()
    },
    javaFXPlugin("intellij.javaFX.community"),
    plugin("intellij.terminal") {
      withResource("resources/.zshrc", "")
      withResource("resources/jediterm-bash.in", "")
      withResource("resources/fish/config.fish", "fish")
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
    plugin("intellij.statsCollector") {
      withModule("intellij.statsCollector.logEvents")
      withModule("intellij.statsCollector.completionRanker")
    },
    plugin("intellij.jps.cache")
  ]

  static PluginLayout androidPlugin(Map<String, String> additionalModulesToJars) {
    // the following is copied from https://android.googlesource.com/platform/tools/idea/+/studio-master-dev/build/groovy/org/jetbrains/intellij/build/AndroidStudioProperties.groovy
    plugin("intellij.android.plugin") {
      directoryName = "android"
      mainJarName = "android.jar"
      withModule("intellij.android.common", "android-common.jar", null)
      withModule("intellij.android.buildCommon", "build-common.jar", null)
      withModule("intellij.android.rt", "android-rt.jar", null)

      withModule("intellij.android.core", "android.jar", null)
      withModule("intellij.android.adb", "android.jar")
      withModule("intellij.android.app-inspection", "android.jar")
      withModule("intellij.android.app-inspection.ide", "android.jar")
      withModule("intellij.android.databinding", "android.jar")
      withModule("intellij.android.debuggers", "android.jar")
      withModule("intellij.android.lang", "android.jar")
      withModule("intellij.android.lang-databinding", "android.jar")
      withModule("intellij.android.mlkit", "android.jar")
      withModule("intellij.android.room", "android.jar")
      withModule("intellij.android.plugin", "android.jar")
      withModule("intellij.android.artwork")
      withModule("intellij.android.build-attribution", "android.jar")
      withModule("intellij.android.observable", "android.jar")
      withModule("intellij.android.observable.ui", "android.jar")
      withModuleLibrary("precompiled-flags", "android.sdktools.flags", "")
      withModule("intellij.android.layout-inspector", "android.jar")
      withModule("intellij.android.layout-ui", "android.jar")
      withModule("intellij.android.transport", "android.jar")
      withModule("intellij.android.designer", "android.jar")
      withModule("intellij.android.compose-designer", "android.jar")
      withModule("intellij.android.designer.customview", "android.jar")
      withModule("intellij.android.naveditor", "android.jar")
      withModule("intellij.android.sdkUpdates", "android.jar")
      withModule("intellij.android.wizard", "android.jar")
      withModule("intellij.android.wizard.model", "android.jar")
      withModuleLibrary("precompiled-wizardTemplate.plugin", "android.sdktools.wizardTemplate.plugin", "")
      withModuleLibrary("precompiled-wizardTemplate.impl", "android.sdktools.wizardTemplate.impl", "")
      withModule("intellij.android.profilersAndroid", "android.jar")
      withModule("intellij.android.deploy", "android.jar")
      withModule("intellij.android.kotlin.idea", "android-kotlin.jar")
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
      withModule("intellij.android.gradle-tooling", "android.jar")
      withModule("intellij.android.gradle-tooling.impl", "android.jar")
      withModule("intellij.android.resources-base", "android.jar")
      withModule("intellij.android.android-layout-inspector", "android.jar")
      /* do not put into IJ android plugin: assistant, connection-assistant, whats-new-assistant */
      withModule("intellij.android.lint", "lint-ide.jar")
      withModule("intellij.android.adt.ui", "adt-ui.jar")
      withModule("intellij.android.adt.ui.model", "adt-ui.jar")
      withModuleLibrary("precompiled-repository", "android.sdktools.repository", "")

      withModuleLibrary("precompiled-db-baseLibrary", "android.sdktools.db-baseLibrary", "")
      withModuleLibrary("precompiled-db-baseLibrarySupport", "android.sdktools.db-baseLibrarySupport", "")
      withModuleLibrary("precompiled-db-compilerCommon", "android.sdktools.db-compilerCommon", "")
      withModuleLibrary("precompiled-db-compiler", "android.sdktools.db-compiler", "")

      withModuleLibrary("precompiled-sdklib", "android.sdktools.sdklib", "")
      withModuleLibrary("precompiled-sdk-common", "android.sdktools.sdk-common", "")

      withModule("intellij.android.layoutlib-loader", "layoutlib-loader.jar")

      // from AOSP's plugin("intellij.android.layoutlib"). Force layoutlib-standard. //
      withModuleLibrary("precompiled-layoutlib-api", "android.sdktools.layoutlib-api", "")
      withModuleLibrary("layoutlib-jre11-27.0.0.0.jar", "intellij.android.layoutlib", "")
      //////////////////////////////////////////////////////

      withModuleLibrary("precompiled-manifest-merger", "android.sdktools.manifest-merger", "")
      withModuleLibrary("precompiled-chunkio", "android.sdktools.chunkio", "")
      withModuleLibrary("precompiled-pixelprobe", "android.sdktools.pixelprobe", "")

      withModuleLibrary("precompiled-binary-resources", "android.sdktools.binary-resources", "")
      withModuleLibrary("precompiled-analyzer", "android.sdktools.analyzer", "")
      withModuleLibrary("precompiled-ddmlib", "android.sdktools.ddmlib", "")
      withModuleLibrary("precompiled-dvlib", "android.sdktools.dvlib", "")
      withModuleLibrary("precompiled-deployer", "android.sdktools.deployer", "")
      withModuleLibrary("deploy_java_proto", "android.sdktools.deployer", "") // exported module library
      withModuleLibrary("libjava_version", "android.sdktools.deployer", "") // exported module library
      withModuleLibrary("r8", "android.sdktools.deployer", "") // exported module library

      withModuleLibrary("precompiled-tracer", "android.sdktools.tracer", "")
      withModuleLibrary("precompiled-draw9patch", "android.sdktools.draw9patch", "")
      withModuleLibrary("precompiled-lint-api", "android.sdktools.lint-api", "")
      withModuleLibrary("precompiled-lint-checks", "android.sdktools.lint-checks", "")
      withModuleLibrary("precompiled-ninepatch", "android.sdktools.ninepatch", "")
      withModuleLibrary("precompiled-perflib", "android.sdktools.perflib", "")
      withModuleLibrary("precompiled-builder-model", "android.sdktools.builder-model", "")
      withModuleLibrary("precompiled-builder-test-api", "android.sdktools.builder-test-api", "")
      withModuleLibrary("precompiled-android-annotations", "android.sdktools.android-annotations", "")
      withModuleLibrary("precompiled-layoutinspector", "android.sdktools.layoutinspector", "")

      withModuleLibrary("precompiled-usb-devices", "android.sdktools.usb-devices", "")

      withModule("intellij.android.jps", "jps/android-jps-plugin.jar", null)

      withProjectLibrary("kxml2") //todo[nik] move to module libraries

      withProjectLibrary("asm-tools")
      withResourceFromModule("intellij.android.core", "lib/commons-compress-1.8.1.jar", "lib")
      withResourceFromModule("intellij.android.core", "lib/javawriter-2.2.1.jar", "lib")

      withResourceFromModule("intellij.android.core", "lib/androidWidgets", "lib/androidWidgets")
      withResourceFromModule("intellij.android.artwork", "resources/device-art-resources", "lib/device-art-resources")
      withResourceFromModule("intellij.android.core", "lib/sampleData", "lib/sampleData")
      withResourceArchive("../android/annotations", "lib/androidAnnotations.jar")

      // here go some differences from original Android Studio layout
      def getSingleFile = { BuildContext context, String projectLibName ->
        List<File> libFiles = context.project.libraryCollection
          .findLibrary(projectLibName)
          .getFiles(JpsOrderRootType.COMPILED)
        assert libFiles.size() == 1: "Exactly one file is expected in project library ${projectLibName}"
        return libFiles[0]
      }

      def unzipProjectLib = { BuildContext context, String projectLibName, String dstFileName ->
        File dstFile = new File(dstFileName)
        context.ant.invokeMethod("unzip", [
            src: getSingleFile(context, projectLibName),
            dest: dstFile
          ]
        )
        return dstFile
      }

      // contents of "/plugins/android/lib/layoutlib/" will be downloaded by the AndroidPlugin on demand

      withGeneratedResources(new ResourcesGenerator() {
        @Override
        File generateResources(BuildContext context) {
          return unzipProjectLib(
            context, "org.jetbrains.intellij.deps.android.tools.base:templates", "$context.paths.temp/andorid-plugin/templates"
          )
        }
      }, "lib/templates")

      withProjectLibrary("transport-proto")
      withProjectLibrary("perfetto-proto")
      withProjectLibrary("studio-proto")
      withProjectLibrary("studio-grpc")
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
    }
  }

  static PluginLayout javaFXPlugin(String mainModuleName) {
    plugin(mainModuleName) {
      directoryName = "javaFX"
      mainJarName = "javaFX.jar"
      withModule("intellij.javaFX", mainJarName)
      withModule("intellij.javaFX.jps")
      withModule("intellij.javaFX.common")
      withModule("intellij.javaFX.sceneBuilder", "rt/sceneBuilderBridge.jar")
    }
  }

  static PluginLayout groovyPlugin(List<String> additionalModules) {
    plugin("intellij.groovy") {
      directoryName = "Groovy"
      mainJarName = "Groovy.jar"
      withModule("intellij.groovy.psi", mainJarName)
      withModule("intellij.groovy.structuralSearch", mainJarName)
      excludeFromModule("intellij.groovy.psi", "standardDsls/**")
      withModule("intellij.groovy.jps")
      withModule("intellij.groovy.rt")
      withModule("intellij.groovy.constants.rt")
      withResource("groovy-psi/resources/standardDsls", "lib/standardDsls")
      withResource("hotswap/gragent.jar", "lib/agent")
      withResource("groovy-psi/resources/conf", "lib")
      additionalModules.each {
        withModule(it)
      }
      doNotCreateSeparateJarForLocalizableResources()
    }
  }
}
