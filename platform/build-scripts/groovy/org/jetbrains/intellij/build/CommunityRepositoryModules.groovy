/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.PluginLayout

import static org.jetbrains.intellij.build.impl.PluginLayout.plugin

/**
 * @author nik
 */
@CompileStatic
class CommunityRepositoryModules {
  static List<String> PLATFORM_API_MODULES = [
    "analysis-api",
    "built-in-server-api",
    "core-api",
    "diff-api",
    "dvcs-api",
    "editor-ui-api",
    "external-system-api",
    "indexing-api",
    "jps-model-api",
    "lang-api",
    "lvcs-api",
    "platform-api",
    "projectModel-api",
    "remote-servers-agent-rt",
    "remote-servers-api",
    "tasks-platform-api",
    "usageView",
    "vcs-api-core",
    "vcs-api",
    "vcs-log-api",
    "vcs-log-graph-api",
    "xdebugger-api",
    "xml-analysis-api",
    "xml-openapi",
    "xml-psi-api",
    "xml-structure-view-api",
    "uast-common",
  ]

  static List<String> PLATFORM_IMPLEMENTATION_MODULES = [
    "analysis-impl",
    "built-in-server",
    "core-impl",
    "credential-store",
    "diff-impl",
    "dvcs-impl",
    "editor-ui-ex",
    "images",
    "indexing-impl",
    "jps-model-impl",
    "jps-model-serialization",
    "json",
    "lang-impl",
    "lvcs-impl",
    "platform-impl",
    "projectModel-impl",
    "protocol-reader-runtime",
    "RegExpSupport",
    "relaxng",
    "remote-servers-impl",
    "script-debugger-backend",
    "script-debugger-ui",
    "smRunner",
    "spellchecker",
    "structure-view-impl",
    "tasks-platform-impl",
    "testRunner",
    "vcs-impl",
    "vcs-log-graph",
    "vcs-log-impl",
    "xdebugger-impl",
    "xml-analysis-impl",
    "xml-psi-impl",
    "xml-structure-view-impl",
    "xml",
    "configuration-store-impl",
  ]

  /**
   * Specifies non-trivial layout for all plugins which sources are located in 'community' and 'contrib' repositories
   */
  static List<PluginLayout> COMMUNITY_REPOSITORY_PLUGINS = [
    plugin("ant") {
      mainJarName = "antIntegration.jar"
      withModule("ant-jps-plugin")
    },
    plugin("ui-designer") {
      directoryName = "uiDesigner"
      mainJarName = "uiDesigner.jar"
      withJpsModule("ui-designer-jps-plugin")
    },
    plugin("properties") {
      withModule("properties-psi-api", "properties.jar")
      withModule("properties-psi-impl", "properties.jar")
    },
    plugin("git4idea") {
      withModule("git4idea-rt", "git4idea-rt.jar", false)
      withOptionalModule("remote-servers-git")
      withOptionalModule("remote-servers-git-java", "remote-servers-git.jar")
    },
    plugin("svn4idea") {
      withResource("lib/licenses", "lib/licenses")
    },
    plugin("cvs-plugin") {
      directoryName = "cvsIntegration"
      mainJarName = "cvsIntegration.jar"
      withModule("javacvs-src")
      withModule("smartcvs-src")
      withModule("cvs-core", "cvs_util.jar")
    },
    plugin("xpath") {
      withModule("xslt-rt", "rt/xslt-rt.jar")
    },
    plugin("IntelliLang") {
      withOptionalModule("IntelliLang-java", "IntelliLang.jar")
      withOptionalModule("IntelliLang-xml", "IntelliLang.jar")
      withOptionalModule("intellilang-jps-plugin", "intellilang-jps-plugin.jar")
      doNotCreateSeparateJarForLocalizableResources()
    },
    plugin("tasks-core") {
      directoryName = "tasks"
      withModule("tasks-api")
      withModule("jira")
      withOptionalModule("tasks-java")
      doNotCreateSeparateJarForLocalizableResources()
    },
    plugin("xslt-debugger") {
      withModule("xslt-debugger-engine")
      withModule("xslt-debugger-engine-impl", "rt/xslt-debugger-engine-impl.jar")
      withModuleLibrary("Saxon-6.5.5", "xslt-debugger-engine-impl", "rt")
      withModuleLibrary("Saxon-9HE", "xslt-debugger-engine-impl", "rt")
      withModuleLibrary("Xalan-2.7.1", "xslt-debugger-engine-impl", "rt")
      //todo[nik] unmark 'lib' directory as source root instead
      excludeFromModule("xslt-debugger-engine-impl", "rmi-stubs.jar")
      excludeFromModule("xslt-debugger-engine-impl", "saxon.jar")
      excludeFromModule("xslt-debugger-engine-impl", "saxon9he.jar")
      excludeFromModule("xslt-debugger-engine-impl", "serializer.jar")
      excludeFromModule("xslt-debugger-engine-impl", "xalan.jar")
    },
    plugin("Edu-Python") {
      withResource("resources/courses", "lib/courses")
      excludeFromModule("Edu-Python", "courses/**")
    },
    plugin("maven") {
      withModule("maven-jps-plugin")
      withModule("maven-server-api")
      withModule("maven2-server-impl")
      withModule("maven3-server-common")
      withModule("maven30-server-impl")
      withModule("maven3-server-impl")
      withModule("maven-artifact-resolver-m2", "artifact-resolver-m2.jar")
      withModule("maven-artifact-resolver-common", "artifact-resolver-m2.jar")
      withModule("maven-artifact-resolver-m3", "artifact-resolver-m3.jar")
      withModule("maven-artifact-resolver-common", "artifact-resolver-m3.jar")
      withModule("maven-artifact-resolver-m31", "artifact-resolver-m31.jar")
      withModule("maven-artifact-resolver-common", "artifact-resolver-m31.jar")
      withResource("maven3-server-impl/lib/maven3", "lib/maven3")
      withResource("maven3-server-common/lib", "lib/maven3-server-lib")
      withResource("maven2-server-impl/lib/maven2", "lib/maven2")
      withModuleLibrary("JAXB", "maven2-server-impl", "maven2-server-lib")
      [
        "activation-1.1.jar",
        "archetype-common-2.0-alpha-4-SNAPSHOT.jar",
        "commons-beanutils.jar",
        "maven-dependency-tree-1.2.jar",
        "mercury-artifact-1.0-alpha-6.jar",
        "nexus-indexer-1.2.3.jar",
        "plexus-utils-1.5.5.jar"
      ].each {withResource("maven2-server-impl/lib/$it", "lib/maven2-server-lib")}
      doNotCopyModuleLibrariesAutomatically([
        "maven2-server-impl", "maven3-server-common", "maven3-server-impl", "maven30-server-impl",
        "maven-artifact-resolver-common", "maven-artifact-resolver-m2", "maven-artifact-resolver-m3", "maven-artifact-resolver-m31"
      ])
    },
    plugin("gradle") {
      withModule("gradle-jps-plugin")
      withModule("gradle-tooling-extension-api")
      withModule("gradle-tooling-extension-impl")
      withProjectLibrary("Kryo")
      withProjectLibrary("Gradle")
    },
    plugin("junit") {
      mainJarName = "idea-junit.jar"
      withModule("junit_rt", "junit-rt.jar")
      withModule("junit5_rt", "junit5-rt.jar")
      withProjectLibrary("JUnit5")
    },
    plugin("ByteCodeViewer") {
      mainJarName = "byteCodeViewer.jar"
    },
    plugin("testng") {
      mainJarName = "testng-plugin.jar"
      withModule("testng_rt", mainJarName)
      withProjectLibrary("TestNG")
    },
    plugin("devkit") {
      withModule("devkit-jps-plugin")
    },
    plugin("eclipse") {
      withModule("eclipse-jps-plugin", "eclipse-jps-plugin.jar", false)
      withModule("common-eclipse-util")
    },
    plugin("coverage") {
      withModule("coverage-common", mainJarName)
      withModule("coverage_rt")
      withProjectLibrary("JaCoCo") //todo[nik] convert to module library
    },
    plugin("java-decompiler-plugin") {
      directoryName = "java-decompiler"
      mainJarName = "java-decompiler.jar"
      withModule("java-decompiler-engine", mainJarName)
      doNotCreateSeparateJarForLocalizableResources()
    },
    javaFXPlugin("javaFX-CE"),
    plugin("terminal") {
      withResource("resources/.zshrc", "")
      withResource("resources/jediterm-bash.in", "")
      withResource("resources/fish/config.fish", "fish")
    }
  ]

  static PluginLayout androidPlugin(Map<String, String> additionalModulesToJars) {
    plugin("android-plugin") {
      mainJarName = "android.jar"
      directoryName = "android"
      withModule("android", "android.jar")
      withModule("observable", "android.jar")
      withModule("wizard", "android.jar")
      withModule("sdk-updates", "android.jar")
      withModule("designer", "android.jar")
      withModule("manifest-merger")
      withModule("repository")
      withModule("common", "android-common.jar")
      withModule("android-common", "android-common.jar", false)
      withModule("android-rt", "android-rt.jar", false)
      withModule("android-annotations", "androidAnnotations.jar")
      withModule("sdklib", "sdklib.jar")
      withModule("sdk-common", "sdk-common.jar")
      withModule("layoutlib-api", "layoutlib-api.jar")
      withModule("layoutlib", "layoutlib-loader.jar")
      withModule("adt-ui", "adt-ui.jar")
      withModule("adt-ui-model", "adt-ui.jar")
      withModule("sherpa-ui", "constraint-layout.jar")
      withModule("pixelprobe", "pixalprobe.jar")
      withModule("manifest-merger", "manifest-merger.jar")
      withModule("assetstudio", "sdk-tools.jar")
      withModule("ddmlib", "sdk-tools.jar")
      withModule("dvlib", "sdk-tools.jar")
      withModule("draw9patch", "sdk-tools.jar")
      withModule("lint-api", "sdk-tools.jar")
      withModule("lint-checks", "sdk-tools.jar")
      withModule("ninepatch", "sdk-tools.jar")
      withModule("perflib", "sdk-tools.jar")
      withModule("builder-model", "sdk-tools.jar")
      withModule("builder-test-api", "sdk-tools.jar")
      withModule("instant-run-common", "sdk-tools.jar")
      withModule("instant-run-client", "sdk-tools.jar")
      withModule("instant-run-runtime", "sdk-tools.jar")
      withModule("android-gradle-jps", "jps/android-gradle-jps.jar", false)
      withModule("android-jps-plugin", "jps/android-jps-plugin.jar", false)
      withProjectLibrary("freemarker-2.3.20") //todo[nik] move to module libraries
      withProjectLibrary("jgraphx-3.4.0.1") //todo[nik] move to module libraries
      withProjectLibrary("kxml2") //todo[nik] move to module libraries
      withProjectLibrary("lombok-ast") //todo[nik] move to module libraries
      withProjectLibrary("layoutlib") //todo[nik] move to module libraries
      withResource("../android/device-art-resources", "lib/device-art-resources")
      withResourceFromModule("layoutlib-resources", ".", "lib/layoutlib")
      withResourceFromModule("sdklib", "../templates", "lib/templates")
      withResourceArchive("../android/annotations", "lib/androidAnnotations.jar")
      withResource("../android/lib/antlr4-runtime-4.5.3.jar", "lib")
      withResource("../android/lib/asm-5.0.3.jar", "lib")
      withResource("../android/lib/asm-analysis-5.0.3.jar", "lib")
      withResource("../android/lib/asm-tree-5.0.3.jar", "lib")
      withResource("../android/lib/commons-io-2.4.jar", "lib")
      withResource("../android/lib/commons-compress-1.8.1.jar", "lib")
      withResource("../android/lib/javawriter-2.2.1.jar", "lib")
      withResource("../android/lib/juniversalchardet-1.0.3.jar", "lib")
      withResource("../android/lib/layoutlib.jar", "lib")
      withResource("../android/lib/google-analytics-library.jar", "lib")
      withResource("../android/lib/gluegen-rt.jar", "lib")
      withResource("../android/lib/gluegen-rt-natives-linux-amd64.jar", "lib")
      withResource("../android/lib/gluegen-rt-natives-linux-i586.jar", "lib")
      withResource("../android/lib/gluegen-rt-natives-macosx-universal.jar", "lib")
      withResource("../android/lib/gluegen-rt-natives-windows-amd64.jar", "lib")
      withResource("../android/lib/gluegen-rt-natives-windows-i586.jar", "lib")
      withProjectLibrary("jogl-all") //todo[nik] move to module libraries
      withResource("../android/lib/jogl-all-natives-linux-amd64.jar", "lib")
      withResource("../android/lib/jogl-all-natives-linux-i586.jar", "lib")
      withResource("../android/lib/jogl-all-natives-macosx-universal.jar", "lib")
      withResource("../android/lib/jogl-all-natives-windows-amd64.jar", "lib")
      withResource("../android/lib/jogl-all-natives-windows-i586.jar", "lib")
      withResource("../android/lib/androidWidgets", "lib/androidWidgets")
      additionalModulesToJars.entrySet().each {
        withModule(it.key, it.value)
      }
    }
  }

  static PluginLayout javaFXPlugin(String mainModuleName) {
    plugin(mainModuleName) {
      directoryName = "javaFX"
      mainJarName = "javaFX.jar"
      withModule("javaFX", mainJarName)
      withModule("javaFX-jps-plugin")
      withModule("common-javaFX-plugin")
      withProjectLibrary("SceneBuilderKit") //todo[nik] move to module libraries
    }
  }

  static PluginLayout groovyPlugin(List<String> additionalModules) {
    plugin("jetgroovy") {
      directoryName = "Groovy"
      mainJarName = "Groovy.jar"
      withModule("groovy-psi", mainJarName)
      withModule("structuralsearch-groovy", mainJarName)
      excludeFromModule("groovy-psi", "standardDsls/**")
      withModule("groovy-jps-plugin")
      withModule("groovy_rt")
      withModule("groovy-rt-constants")
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