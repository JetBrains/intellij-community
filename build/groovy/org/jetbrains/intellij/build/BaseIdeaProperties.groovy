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
    "java-analysis-api",
    "java-indexing-api",
    "java-psi-api",
    "jsp-openapi",
    "jsp-base-openapi",
    "openapi",
    "remote-servers-java-api",
    "testFramework-java"
  ]
  protected static final List<String> JAVA_IMPLEMENTATION_MODULES = [
    "compiler-impl",
    "debugger-impl",
    "dom-impl",
    "execution-impl",
    "external-system-impl",
    "idea-ui",
    "java-analysis-impl",
    "java-indexing-impl",
    "java-impl",
    "java-psi-impl",
    "java-structure-view",
    "jsp-spi",
    "manifest",
    "remote-servers-java-impl",
    "testFramework",
    "tests_bootstrap",
    "ui-designer-core",
    "uast-java"
  ]
  protected static final List<String> BUNDLED_PLUGIN_MODULES = [
    "copyright", "properties", "terminal", "editorconfig", "settings-repository", "yaml",
    "tasks-core", "tasks-java",
    "maven", "gradle",
    "git4idea", "remote-servers-git", "remote-servers-git-java", "svn4idea", "hg4idea", "github", "cvs-plugin",
    "jetgroovy", "junit", "testng", "xpath", "xslt-debugger", "android-plugin", "javaFX-CE",
    "java-i18n", "ant", "ui-designer", "ByteCodeViewer", "coverage", "java-decompiler-plugin", "devkit", "eclipse",
    "IntelliLang", "IntelliLang-java", "IntelliLang-xml", "intellilang-jps-plugin"
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
    productLayout.additionalPlatformJars.putAll("jps-model.jar", ["jps-model-impl", "jps-model-serialization"])
    productLayout.additionalPlatformJars.putAll("resources.jar", ["resources", "resources-en"])
    productLayout.additionalPlatformJars.
      putAll("javac2.jar", ["javac2", "forms-compiler", "forms_rt", "instrumentation-util", "instrumentation-util-8", "javac-ref-scanner-8"])
    productLayout.additionalPlatformJars.putAll("annotations-java8.jar", ["annotations-common", "annotations-java8"])

    productLayout.platformLayoutCustomizer = { PlatformLayout layout ->
      layout.customize {
        withModule("java-runtime", "idea_rt.jar", false)
        withProjectLibrary("Eclipse")
        withProjectLibrary("jgoodies-common")
        withProjectLibrary("jgoodies-looks")
        withProjectLibrary("commons-net")
        withProjectLibrary("snakeyaml")
        withoutProjectLibrary("Ant")
        withoutProjectLibrary("Gradle")
      }
    } as Consumer<PlatformLayout>

    additionalModulesToCompile = ["jps-standalone-builder"]
    modulesToCompileTests = ["jps-builders"]
    productLayout.buildAllCompatiblePlugins = true
    productLayout.compatiblePluginsToIgnore = ['python-plugin', 'python-community-plugin-resources', 'AWS']
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
