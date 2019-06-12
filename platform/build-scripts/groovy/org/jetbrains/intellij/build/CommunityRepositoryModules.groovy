// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.python.PythonCommunityPluginModules

import static org.jetbrains.intellij.build.impl.PluginLayout.plugin

/**
 * @author nik
 */
@CompileStatic
class CommunityRepositoryModules {
  /**
   * @deprecated use {@link ProductModulesLayout#productApiModules} instead of {@link ProductModulesLayout#platformApiModules} to avoid
   * using this property
   */
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
   * @deprecated use {@link ProductModulesLayout#productImplementationModules} instead of {@link ProductModulesLayout#platformImplementationModules}
   * to avoid using this property
   */
  static List<String> PLATFORM_IMPLEMENTATION_MODULES = [
    "intellij.platform.analysis.impl",
    "intellij.platform.builtInServer.impl",
    "intellij.platform.core.impl",
    "intellij.platform.diff.impl",
    "intellij.platform.editor.ex",
    "intellij.platform.indexing.impl",
    "intellij.platform.lang.impl",
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
    plugin("intellij.java.guiForms.designer") {
      directoryName = "uiDesigner"
      mainJarName = "uiDesigner.jar"
      withModule("intellij.java.guiForms.jps", "jps/ui-designer-jps-plugin.jar")
    },
    plugin("intellij.properties") {
      withModule("intellij.properties.psi", "properties.jar")
      withModule("intellij.properties.psi.impl", "properties.jar")
    },
    plugin("intellij.vcs.git") {
      withModule("intellij.vcs.git.rt", "git4idea-rt.jar", null)
      withModule("intellij.platform.remoteServers.git")
      withModule("intellij.java.remoteServers.git", "remote-servers-git.jar")
    },
    plugin("intellij.vcs.cvs") {
      directoryName = "cvsIntegration"
      mainJarName = "cvsIntegration.jar"
      withModule("intellij.vcs.cvs.javacvs")
      withModule("intellij.vcs.cvs.smartcvs")
      withModule("intellij.vcs.cvs.core", "cvs_util.jar")
    },
    plugin("intellij.xpath") {
      withModule("intellij.xslt.debugger.rt", "rt/xslt-rt.jar")
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
      withModule("intellij.xslt.debugger.engine")
      withModule("intellij.xslt.debugger.engine.impl", "rt/xslt-debugger-engine-impl.jar")
      withModuleLibrary("Saxon-6.5.5", "intellij.xslt.debugger.engine.impl", "rt")
      withModuleLibrary("Saxon-9HE", "intellij.xslt.debugger.engine.impl", "rt")
      withModuleLibrary("Xalan-2.7.2", "intellij.xslt.debugger.engine.impl", "rt")
      //todo[nik] unmark 'lib' directory as source root instead
      excludeFromModule("intellij.xslt.debugger.engine.impl", "rmi-stubs.jar")
      excludeFromModule("intellij.xslt.debugger.engine.impl", "saxon.jar")
      excludeFromModule("intellij.xslt.debugger.engine.impl", "saxon9he.jar")
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
      withResource("maven2-server-impl/lib/maven2", "lib/maven2")
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
      withModule("intellij.gradle.java")
      withModule("intellij.gradle.jps")
      withModule("intellij.gradle.toolingExtension")
      withModule("intellij.gradle.toolingExtension.impl")
      withProjectLibrary("Kryo")
      withProjectLibrary("Gradle")
    },
    plugin("intellij.gradle.java.maven"),
    plugin("intellij.platform.testGuiFramework") {
      //the plugin is for internal use for now so it shouldn't be published
      defaultPublishingSpec = PluginPublishingSpec.DO_NOT_UPLOAD_AUTOMATICALLY
      mainJarName = "testGuiFramework"
      withModule("intellij.platform.testGuiFramework")
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
      withModule("intellij.testng.rt", mainJarName)
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
      withProjectLibrary("cucumber-core")
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
      withResource("lib/bundles", "lib/bundles")
      withResource("lib/themes", "lib/themes")
    },
    PythonCommunityPluginModules.pythonCommunityPluginLayout(),
    // required for android plugin
    plugin("intellij.android.smali") {
      withModule("intellij.android.smali")
    },
    plugin("intellij.statsCollector") {
      bundlingRestrictions.includeInEapOnly = true
      withModule("intellij.statsCollector.features", "features.jar")
      withModule("intellij.statsCollector.logEvents")
      withModule("intellij.statsCollector.completionRanker")
    },
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
      withModule("intellij.android.debuggers", "android.jar")
      withModule("intellij.android.lang", "android.jar")
      withModule("intellij.android.plugin", "android.jar")
      withModule("intellij.android.artwork")
      withModule("intellij.android.observable", "android.jar")
      withModule("intellij.android.observable.ui", "android.jar")
      withModule("android.sdktools.flags", "android.jar")
      withModule("intellij.android.designer", "android.jar")
      withModule("intellij.android.sdkUpdates", "android.jar")
      withModule("intellij.android.wizard", "android.jar")
      withModule("intellij.android.wizard.model", "android.jar")
      withModule("intellij.android.profilersAndroid", "android.jar")
      withModule("intellij.android.deploy", "android.jar")
      withModule("intellij.android.perfdHost", "android-profilers.jar")
      withModule("intellij.android.profilers", "android-profilers.jar")
      withModule("intellij.android.profilers.ui", "android-profilers.jar")
      withModule("intellij.android.profilers.atrace", "android-profilers.jar")
      withModule("intellij.android.nativeSymbolizer", "android.jar")
      withModule("intellij.android.apkanalyzer", "android.jar")
      withModule("intellij.android.projectSystem", "android.jar")
      withModule("intellij.android.projectSystem.gradle", "android.jar")
      withModule("intellij.android.layoutInspector", "android.jar")
      withModule("intellij.android.adt.ui", "adt-ui.jar")
      withModule("intellij.android.adt.ui.model", "adt-ui.jar")
      withModule("intellij.android.layoutlib", "layoutlib-loader.jar")

      withModule("android.sdktools.instant-run-client", "sdk-tools.jar")
      withModule("android.sdktools.instant-run-common", "sdk-tools.jar")

      withModule("intellij.android.jps", "jps/android-jps-plugin.jar", null)

      withProjectLibrary("freemarker") //todo[nik] move to module libraries
      withProjectLibrary("kxml2") //todo[nik] move to module libraries
      withProjectLibrary("layoutlib") //todo[nik] move to module libraries

      withResourceFromModule("intellij.android.core","lib/asm-5.0.3.jar", "lib")
      withResourceFromModule("intellij.android.core","lib/asm-analysis-5.0.3.jar", "lib")
      withResourceFromModule("intellij.android.core","lib/asm-tree-5.0.3.jar", "lib")
      withResourceFromModule("intellij.android.core","lib/commons-compress-1.8.1.jar", "lib")
      withResourceFromModule("intellij.android.core","lib/javawriter-2.2.1.jar", "lib")
      withResourceFromModule("intellij.android.core","lib/android-extensions-ide.jar", "lib")
      withResourceFromModule("intellij.android.core","lib/android-kotlin.jar", "lib")

      withResourceFromModule("intellij.android.core","lib/androidWidgets", "lib/androidWidgets")
      withResourceFromModule("intellij.android.artwork","resources/device-art-resources", "lib/device-art-resources")
      withResourceFromModule("intellij.android.core","lib/sampleData", "lib/sampleData")
      withResourceFromModule("intellij.android.adt.ui", "lib/libwebp/linux", "lib/libwebp/linux")
      withResourceFromModule("intellij.android.adt.ui", "lib/libwebp/mac", "lib/libwebp/mac")
      withResourceFromModule("intellij.android.adt.ui", "lib/libwebp/win", "lib/libwebp/win")
      withResourceArchive("../android/annotations", "lib/androidAnnotations.jar")

      // here go some differences from original Android Studio layout
      withResourceFromModule("android.sdktools.layoutlib-resources", ".", "lib/layoutlib") // todo replace this with runtime downloading
      withResourceFromModule("android.sdktools.sdklib", "../templates", "lib/templates")

      withProjectLibrary("studio-profiler-grpc-1.0-jarjar")
      withProjectLibrary("com.android.tools.analytics-library:crash")
      withProjectLibrary("com.android.tools.analytics-library:protos")
      withProjectLibrary("com.android.tools.analytics-library:shared")
      withProjectLibrary("com.android.tools.analytics-library:tracker")
      withProjectLibrary("com.android.tools:annotations")
      withProjectLibrary("com.android.tools.apkparser:apkanalyzer")
      withProjectLibrary("com.android.tools.apkparser:binary-resources")
      withProjectLibrary("com.android.tools.layoutlib:layoutlib-api")
      withProjectLibrary("com.android.tools.lint:lint-api")
      withProjectLibrary("com.android.tools.lint:lint-checks")
      withProjectLibrary("com.android.tools:sdk-common")
      withProjectLibrary("com.android.tools:dvlib")
      withProjectLibrary("com.android.tools:sdklib")
      withProjectLibrary("com.android.tools:common")
      withProjectLibrary("com.android.tools:repository")
      withProjectLibrary("com.android.tools:ninepatch")
      withProjectLibrary("com.android.tools.ddms:ddmlib")
      withProjectLibrary("com.android.tools.build:manifest-merger")
      withProjectLibrary("com.android.tools.build:builder-model")
      withProjectLibrary("com.android.tools.build:builder-test-api")
      withProjectLibrary("com.android.tools.pixelprobe:pixelprobe")

      withProjectLibrary("org.jetbrains.intellij.deps.android.tools:perflib")
      withProjectLibrary("org.jetbrains.intellij.deps.android.tools:layoutInspector")

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
      withProjectLibrary("SceneBuilderKit") //todo[nik] move to module libraries
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