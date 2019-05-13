// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.PlatformLayout

import java.util.function.Consumer

/**
 * @author nik
 */
abstract class BaseIdeaProperties extends ProductProperties {
  protected static final List<String> JAVA_API_MODULES = [
    "intellij.java.compiler",
    "intellij.java.debugger",
    "intellij.xml.dom",
    "intellij.java.execution",
    "intellij.java.remoteServers",
    "intellij.java.testFramework",
    "intellij.platform.testFramework.core",
    "intellij.platform.uast.tests"
  ]
  protected static final List<String> JAVA_IMPLEMENTATION_MODULES = [
    "intellij.java.compiler.impl",
    "intellij.java.debugger.impl",
    "intellij.java.debugger.memory.agent",
    "intellij.xml.dom.impl",
    "intellij.java.execution.impl",
    "intellij.platform.externalSystem.impl",
    "intellij.java.ui",
    "intellij.java.structureView",
    "intellij.java.manifest",
    "intellij.java.remoteServers.impl",
    "intellij.platform.testFramework",
    "intellij.tools.testsBootstrap",
    "intellij.uiDesigner"
  ]
  protected static final List<String> BUNDLED_PLUGIN_MODULES = [
    "intellij.copyright",
    "intellij.properties",
    "intellij.terminal",
    "intellij.editorconfig",
    "intellij.settingsRepository",
    "intellij.configurationScript",
    "intellij.yaml",
    "intellij.tasks.core",
    "intellij.tasks.java",
    "intellij.maven",
    "intellij.gradle",
    "intellij.vcs.git",
    "intellij.platform.remoteServers.git",
    "intellij.java.remoteServers.git",
    "intellij.vcs.svn",
    "intellij.vcs.hg",
    "intellij.vcs.github",
    "intellij.vcs.cvs",
    "intellij.groovy",
    "intellij.junit",
    "intellij.testng",
    "intellij.xpath",
    "intellij.xslt.debugger",
    "intellij.android.plugin",
    "intellij.javaFX.community",
    "intellij.java.i18n",
    "intellij.ant",
    "intellij.java.guiForms.designer",
    "intellij.java.byteCodeViewer",
    "intellij.java.coverage",
    "intellij.java.decompiler",
    "intellij.devkit",
    "intellij.eclipse",
    "intellij.platform.langInjection",
    "intellij.java.langInjection",
    "intellij.xml.langInjection",
    "intellij.java.langInjection.jps",
    "intellij.java.debugger.streams",
    "intellij.android.smali",
    "intellij.statsCollector"
  ]
  protected static final Map<String, String> CE_CLASS_VERSIONS = [
    "": "1.8",
    "lib/idea_rt.jar": "1.3",
    "lib/forms_rt.jar": "1.4",
    "lib/annotations.jar": "1.5",
    "lib/util.jar": "1.6",
    "lib/rt/debugger-agent.jar": "1.6",
    "lib/rt/debugger-agent-storage.jar": "1.6",
    "lib/external-system-rt.jar": "1.6",
    "lib/jshell-frontend.jar": "1.9",
    "lib/sa-jdwp": "",  // ignored
    "plugins/Groovy/lib/groovy_rt.jar": "1.5",
    "plugins/Groovy/lib/groovy-rt-constants.jar": "1.5",
    "plugins/coverage/lib/coverage_rt.jar": "1.5",
    "plugins/junit/lib/junit-rt.jar": "1.3",
    "plugins/gradle/lib/gradle-tooling-extension-api.jar": "1.6",
    "plugins/gradle/lib/gradle-tooling-extension-impl.jar": "1.6",
    "plugins/maven/lib/maven-server-api.jar": "1.6",
    "plugins/maven/lib/maven2-server-impl.jar": "1.6",
    "plugins/maven/lib/maven3-server-common.jar": "1.6",
    "plugins/maven/lib/maven30-server-impl.jar": "1.6",
    "plugins/maven/lib/maven3-server-impl.jar": "1.6",
    "plugins/maven/lib/artifact-resolver-m2.jar": "1.6",
    "plugins/maven/lib/artifact-resolver-m3.jar": "1.6",
    "plugins/maven/lib/artifact-resolver-m31.jar": "1.6",
    "plugins/xpath/lib/rt/xslt-rt.jar": "1.4",
    "plugins/xslt-debugger/lib/xslt-debugger-engine.jar": "1.5",
    "plugins/xslt-debugger/lib/rt/xslt-debugger-engine-impl.jar": "1.5",
    "plugins/cucumber-java/lib/cucumber-jvmFormatter.jar": "1.6"
  ]

  BaseIdeaProperties() {
    productLayout.mainJarName = "idea.jar"
    productLayout.moduleExcludes.put("intellij.java.resources.en", "search/searchableOptions.xml")

    productLayout.additionalPlatformJars.put("external-system-rt.jar", "intellij.platform.externalSystem.rt")
    productLayout.additionalPlatformJars.put("external-system-impl.jar", "intellij.platform.externalSystem.impl")
    productLayout.additionalPlatformJars.put("jps-launcher.jar", "intellij.platform.jps.build.launcher")
    productLayout.additionalPlatformJars.put("jps-builders.jar", "intellij.platform.jps.build")
    productLayout.additionalPlatformJars.put("jps-builders-6.jar", "intellij.platform.jps.build.javac.rt")
    productLayout.additionalPlatformJars.put("aether-dependency-resolver.jar", "intellij.java.aetherDependencyResolver")
    productLayout.additionalPlatformJars.put("jshell-protocol.jar", "intellij.java.jshell.protocol")
    productLayout.additionalPlatformJars.putAll("resources.jar", ["intellij.java.resources", "intellij.java.resources.en"])
    productLayout.additionalPlatformJars.
      putAll("javac2.jar", ["intellij.java.compiler.antTasks", "intellij.java.guiForms.compiler", "intellij.java.guiForms.rt", "intellij.java.compiler.instrumentationUtil", "intellij.java.compiler.instrumentationUtil.java8", "intellij.java.jps.javacRefScanner8"])

    def JAVA_API_JAR = "java-api.jar"
    def JAVA_IMPL_JAR = "java-impl.jar"
    productLayout.additionalPlatformJars.putAll(JAVA_API_JAR, [])
    productLayout.additionalPlatformJars.putAll(JAVA_IMPL_JAR, [])

    productLayout.platformLayoutCustomizer = { PlatformLayout layout ->
      layout.customize {
        def JAVA_RESOURCES_JAR = "java_resources_en.jar"
        withModule("intellij.java.analysis", JAVA_API_JAR, JAVA_RESOURCES_JAR)
        withModule("intellij.jvm.analysis", JAVA_API_JAR, JAVA_RESOURCES_JAR)
        withModule("intellij.java.indexing", JAVA_API_JAR, JAVA_RESOURCES_JAR)
        withModule("intellij.java.psi", JAVA_API_JAR, JAVA_RESOURCES_JAR)
        withModule("intellij.java", JAVA_API_JAR, JAVA_RESOURCES_JAR)
        withModule("intellij.jsp.base", JAVA_API_JAR, JAVA_RESOURCES_JAR)
        withModule("intellij.jsp", JAVA_API_JAR, JAVA_RESOURCES_JAR)
        withModule("intellij.platform.uast", JAVA_API_JAR, JAVA_RESOURCES_JAR)

        withModule("intellij.java.analysis.impl", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
        withModule("intellij.jvm.analysis.impl", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
        withModule("intellij.java.indexing.impl", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
        withModule("intellij.java.psi.impl", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
        withModule("intellij.java.impl", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
        withModule("intellij.jsp.spi", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
        withModule("intellij.java.uast", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)

        withModule("intellij.java.rt", "idea_rt.jar", null)
        withArtifact("debugger-agent", "rt")
        withArtifact("debugger-agent-storage", "rt")
        withProjectLibrary("Eclipse")
        withProjectLibrary("jgoodies-common")
        withProjectLibrary("commons-net")
        withProjectLibrary("snakeyaml")
        withProjectLibrary("jetbrains-annotations")
        withoutProjectLibrary("Ant")
        withoutProjectLibrary("Gradle")
        removeVersionFromProjectLibraryJarNames("jetbrains-annotations")
        withProjectLibrary("JUnit3")
        removeVersionFromProjectLibraryJarNames("JUnit3") //for compatibility with users projects which refer to IDEA_HOME/lib/junit.jar
      }
    } as Consumer<PlatformLayout>

    additionalModulesToCompile = ["intellij.tools.jps.build.standalone"]
    modulesToCompileTests = ["intellij.platform.jps.build"]
  }

  @Override
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    context.ant.jar(destfile: "$targetDirectory/lib/jdkAnnotations.jar") {
      fileset(dir: "$context.paths.communityHome/java/jdkAnnotations")
    }
    context.ant.copy(todir: "$targetDirectory/lib/ant") {
      fileset(dir: "$context.paths.communityHome/lib/ant") {
        exclude(name: "**/src/**")
      }
    }
    context.ant.copy(todir: "$targetDirectory/plugins/Kotlin") {
      fileset(dir: "$context.paths.kotlinHome")
    }
    context.ant.move(file: "$targetDirectory/lib/annotations.jar", tofile: "$targetDirectory/redist/annotations-java8.jar")
    //for compatibility with users projects which refer to IDEA_HOME/lib/annotations.jar
    context.ant.move(file: "$targetDirectory/lib/annotations-java5.jar", tofile: "$targetDirectory/lib/annotations.jar")
  }
}
