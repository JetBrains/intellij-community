/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
    "ui-designer-jps-plugin",
  ]
  protected static final List<String> BUNDLED_PLUGIN_MODULES = [
    "copyright",
    "properties",
    "terminal",
    "editorconfig",
    "settings-repository",
    "yaml",
    "tasks-core",
    "tasks-java",
    "gradle",
    "git4idea",
    "remote-servers-git",
    "remote-servers-git-java",
    "svn4idea",
    "hg4idea",
    "github",
    "cvs-plugin",
    "jetgroovy",
    "junit",
    "testng",
    "android-plugin",
    "java-i18n",
    "coverage",
    "java-decompiler-plugin",
    "IntelliLang",
    "IntelliLang-java",
    "IntelliLang-xml",
    "intellilang-jps-plugin"
    /* Disabled in Android Studio
    "ant",
    "ByteCodeViewer",
    "devkit",
    "eclipse",
    "javaFX-CE",
    "maven",
    "ui-designer",
    "xpath",
    "xslt-debugger",
    */
  ]

  BaseIdeaProperties() {
    productLayout.mainJarName = "idea.jar"
    productLayout.searchableOptionsModule = "resources-en"

    productLayout.additionalPlatformJars.put("external-system-rt.jar", "external-system-rt")
    productLayout.additionalPlatformJars.put("jps-launcher.jar", "jps-launcher")
    productLayout.additionalPlatformJars.put("jps-builders.jar", "jps-builders")
    productLayout.additionalPlatformJars.put("jps-builders-6.jar", "jps-builders-6")
    productLayout.additionalPlatformJars.putAll("jps-model.jar", ["jps-model-impl", "jps-model-serialization"])
    productLayout.additionalPlatformJars.put("forms_rt.jar", "forms-compiler")
    productLayout.additionalPlatformJars.putAll("resources.jar", ["resources", "resources-en"])
    productLayout.additionalPlatformJars.
      putAll("javac2.jar", ["javac2", "forms-compiler", "forms_rt", "instrumentation-util", "instrumentation-util-8", "javac-ref-scanner-8"])
    /* Disabled in Android Studio:
    productLayout.additionalPlatformJars.putAll("annotations-java8.jar", ["annotations-common", "annotations-java8"])
    */

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
        withoutProjectLibrary("com.twelvemonkeys.imageio:imageio-tiff:3.2.1")
      }
    } as Consumer<PlatformLayout>

    additionalModulesToCompile = ["jps-standalone-builder"]
    modulesToCompileTests = ["jps-builders"]
  }

  @Override
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    context.ant.jar(destfile: "$targetDirectory/lib/jdkAnnotations.jar") {
      fileset(dir: "$context.paths.communityHome/java/jdkAnnotations")
    }
    context.ant.copy(todir: "$targetDirectory/lib") {
      fileset(file: "$context.paths.communityHome/jps/lib/optimizedFileManager.jar")
    }

    // Android Studio: trove4j has JetBrains patches, we must ship its sources
    context.ant.copy(todir: "$targetDirectory/lib/src") {
      fileset(file: "$context.paths.communityHome/lib/src/trove4j_src.jar")
    }

    context.ant.copy(todir: "$targetDirectory/lib/ant") {
      fileset(dir: "$context.paths.communityHome/lib/ant") {
        exclude(name: "**/src/**")
        exclude(name: "**/BUILD")
      }
    }

    if ("true".equalsIgnoreCase(System.getProperty("bundle.kotlin.plugin"))) {
      def currentVersion = "kotlin-plugin-1.1.3-release-Studio3.0-2.zip"
      context.ant.unzip(
        src: "$context.paths.communityHome/../../prebuilts/tools/common/kotlin-plugin/$currentVersion",
        dest: "$targetDirectory/plugins")

       // Gradle plugin
      def currentGradleVersion = "kotlin-m2repository.zip"
      context.ant.unzip(
        src: "$context.paths.communityHome/../../prebuilts/tools/common/kotlin-gradle-plugin/$currentGradleVersion",
        dest: "$targetDirectory/gradle/m2repository")

    }

    /* Disabled in Android Studio:
    context.ant.move(file: "$targetDirectory/lib/annotations-java8.jar", tofile: "$targetDirectory/redist/annotations-java8.jar")
    */
  }
}
