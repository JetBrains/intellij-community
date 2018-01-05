/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */



package org.jetbrains.intellij.build

import com.intellij.util.SystemProperties
import org.jetbrains.intellij.build.impl.PlatformLayout

import java.util.function.Consumer

/**
 * @author nik
 */
abstract class BaseIdeaProperties extends ProductProperties {
  protected static final List<String> JAVA_API_MODULES = [
    "compiler-openapi",
    "debugger-openapi",
    "dom-openapi",
    "execution-openapi",
    "remote-servers-java-api",
    "testFramework-java",
    "testFramework.core"
  ]
  protected static final List<String> JAVA_IMPLEMENTATION_MODULES = [
    "compiler-impl",
    "debugger-impl",
    "dom-impl",
    "execution-impl",
    "external-system-impl",
    "idea-ui",
    "java-structure-view",
    "manifest",
    "remote-servers-java-impl",
    "testFramework",
    "tests_bootstrap",
    "ui-designer-core"
  ]
  protected static final List<String> BUNDLED_PLUGIN_MODULES = [
    "copyright", "properties", "terminal", "editorconfig", "settings-repository", "yaml",
    "tasks-core", "tasks-java",
    "maven", "gradle",
    "git4idea", "remote-servers-git", "remote-servers-git-java", "svn4idea", "hg4idea", "github", "cvs-plugin",
    "jetgroovy", "junit", "testng", "xpath", "xslt-debugger", "android-plugin", "javaFX-CE",
    "java-i18n", "ant", "ui-designer", "ByteCodeViewer", "coverage", "java-decompiler-plugin", "devkit", "eclipse",
    "IntelliLang", "IntelliLang-java", "IntelliLang-xml", "intellilang-jps-plugin", "stream-debugger", "smali"
  ]

  BaseIdeaProperties() {
    productLayout.mainJarName = "idea.jar"
    productLayout.searchableOptionsModule = "resources-en"

    productLayout.additionalPlatformJars.put("external-system-rt.jar", "external-system-rt")
    productLayout.additionalPlatformJars.put("jps-launcher.jar", "jps-launcher")
    productLayout.additionalPlatformJars.put("jps-builders.jar", "jps-builders")
    productLayout.additionalPlatformJars.put("jps-builders-6.jar", "jps-builders-6")
    productLayout.additionalPlatformJars.put("aether-dependency-resolver.jar", "aether-dependency-resolver")
    productLayout.additionalPlatformJars.put("jshell-protocol.jar", "jshell-protocol")
    productLayout.additionalPlatformJars.putAll("resources.jar", ["resources", "resources-en"])
    productLayout.additionalPlatformJars.
      putAll("javac2.jar", ["javac2", "forms-compiler", "forms_rt", "instrumentation-util", "instrumentation-util-8", "javac-ref-scanner-8"])
    productLayout.additionalPlatformJars.putAll("annotations-java8.jar", ["annotations-common", "annotations-java8"])

    def JAVA_API_JAR = "java-api.jar"
    def JAVA_IMPL_JAR = "java-impl.jar"
    productLayout.additionalPlatformJars.putAll(JAVA_API_JAR, [])
    productLayout.additionalPlatformJars.putAll(JAVA_IMPL_JAR, [])

    productLayout.platformLayoutCustomizer = { PlatformLayout layout ->
      layout.customize {
        def JAVA_RESOURCES_JAR = "java_resources_en.jar"
        withModule("java-analysis-api", JAVA_API_JAR, JAVA_RESOURCES_JAR)
        withModule("java-indexing-api", JAVA_API_JAR, JAVA_RESOURCES_JAR)
        withModule("java-psi-api", JAVA_API_JAR, JAVA_RESOURCES_JAR)
        withModule("openapi", JAVA_API_JAR, JAVA_RESOURCES_JAR)
        withModule("jsp-base-openapi", JAVA_API_JAR, JAVA_RESOURCES_JAR)
        withModule("jsp-openapi", JAVA_API_JAR, JAVA_RESOURCES_JAR)
        withModule("uast-common", JAVA_API_JAR, JAVA_RESOURCES_JAR)

        withModule("java-analysis-impl", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
        withModule("java-indexing-impl", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
        withModule("java-psi-impl", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
        withModule("java-impl", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
        withModule("jsp-spi", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)
        withModule("uast-java", JAVA_IMPL_JAR, JAVA_RESOURCES_JAR)

        withModule("java-runtime", "idea_rt.jar", null)
        withArtifact("debugger-agent", "rt")
        withArtifact("debugger-agent-storage", "rt")
        withProjectLibrary("Eclipse")
        withProjectLibrary("jgoodies-common")
        withProjectLibrary("commons-net")
        withProjectLibrary("snakeyaml")
        withoutProjectLibrary("Ant")
        withoutProjectLibrary("Gradle")
        removeVersionFromProjectLibraryJarNames("JUnit3") //for compatibility with users projects which refer to IDEA_HOME/lib/junit.jar
      }
    } as Consumer<PlatformLayout>

    additionalModulesToCompile = ["jps-standalone-builder"]
    modulesToCompileTests = ["jps-builders"]
    productLayout.buildAllCompatiblePlugins = true
    productLayout.prepareCustomPluginRepositoryForPublishedPlugins = SystemProperties.getBooleanProperty('intellij.build.prepare.plugin.repository', false)
  }

  @Override
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    context.ant.jar(destfile: "$targetDirectory/lib/jdkAnnotations.jar") {
      fileset(dir: "$context.paths.communityHome/java/jdkAnnotations")
    }
    context.ant.copy(todir: "$targetDirectory/lib") {
      fileset(file: "$context.paths.communityHome/jps/lib/optimizedFileManager.jar")
    }
    context.ant.copy(todir: "$targetDirectory/lib/ant") {
      fileset(dir: "$context.paths.communityHome/lib/ant") {
        exclude(name: "**/src/**")
      }
    }
    context.ant.copy(todir: "$targetDirectory/plugins/Kotlin") {
      fileset(dir: "$context.paths.kotlinHome")
    }
    context.ant.move(file: "$targetDirectory/lib/annotations-java8.jar", tofile: "$targetDirectory/redist/annotations-java8.jar")
  }
}
