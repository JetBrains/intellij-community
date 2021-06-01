// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.impl.BuildHelper
import org.jetbrains.intellij.build.impl.PlatformLayout

import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Consumer

/**
 * Base class for all editions of IntelliJ IDEA
 */
@CompileStatic
abstract class BaseIdeaProperties extends JetBrainsProductProperties {
  public static final List<String> JAVA_IDE_API_MODULES = [
    "intellij.xml.dom",
    "intellij.java.testFramework",
    "intellij.platform.testFramework.core",
    "intellij.platform.uast.tests",
    "intellij.jsp.base"
  ]
  public static final List<String> JAVA_IDE_IMPLEMENTATION_MODULES = [
    "intellij.xml.dom.impl",
    "intellij.platform.testFramework",
    "intellij.tools.testsBootstrap"
  ]

  protected static final List<String> BUNDLED_PLUGIN_MODULES = [
    "intellij.java.plugin",
    "intellij.java.ide.customization",
    "intellij.copyright",
    "intellij.properties",
    "intellij.properties.resource.bundle.editor",
    "intellij.terminal",
    "intellij.emojipicker",
    "intellij.textmate",
    "intellij.editorconfig",
    "intellij.settingsRepository",
    "intellij.configurationScript",
    "intellij.yaml",
    "intellij.tasks.core",
    "intellij.repository.search",
    "intellij.maven.model",
    "intellij.maven",
    "intellij.externalSystem.dependencyUpdater",
    "intellij.packageSearch",
    "intellij.gradle",
    "intellij.gradle.dependencyUpdater",
    "intellij.android.gradle.dsl",
    "intellij.gradle.java",
    "intellij.gradle.java.maven",
    "intellij.vcs.git",
    "intellij.vcs.svn",
    "intellij.vcs.hg",
    "intellij.vcs.github",
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
    "intellij.java.debugger.streams",
    "intellij.android.smali",
    "intellij.completionMlRanking",
    "intellij.completionMlRankingModels",
    "intellij.statsCollector",
    "intellij.ml.models.local",
    "intellij.sh",
    "intellij.vcs.changeReminder",
    "intellij.filePrediction",
    "intellij.markdown",
    "intellij.webp",
    "intellij.grazie",
    "intellij.featuresTrainer",
    "intellij.vcs.git.featuresTrainer",
    "intellij.lombok"
  ]

  protected static final Map<String, String> CE_CLASS_VERSIONS = [
    ""                                                          : "11",
    "lib/idea_rt.jar"                                           : "1.6",
    "lib/forms_rt.jar"                                          : "1.6",
    "lib/annotations.jar"                                       : "1.6",
    // JAR contains class files for Java 1.8 and 11 (several modules packed into it)
    "lib/util.jar!/com/intellij/serialization/"                  : "1.8",
    "lib/external-system-rt.jar"                                : "1.6",
    "lib/jshell-frontend.jar"                                   : "9",
    "plugins/java/lib/sa-jdwp"                                  : "",  // ignored
    "plugins/java/lib/rt/debugger-agent.jar"                    : "1.6",
    "plugins/java/lib/rt/debugger-agent-storage.jar"            : "1.6",
    "plugins/Groovy/lib/groovy-rt.jar"                          : "1.6",
    "plugins/Groovy/lib/groovy-constants-rt.jar"                : "1.6",
    "plugins/coverage/lib/coverage_rt.jar"                      : "1.6",
    "plugins/javaFX/lib/rt/sceneBuilderBridge.jar"              : "11",
    "plugins/junit/lib/junit-rt.jar"                            : "1.6",
    "plugins/junit/lib/junit5-rt.jar"                           : "1.8",
    "plugins/gradle/lib/gradle-tooling-extension-api.jar"       : "1.6",
    "plugins/gradle/lib/gradle-tooling-extension-impl.jar"      : "1.6",
    "plugins/maven/lib/maven-server-api.jar"                    : "1.6",
    "plugins/maven/lib/maven2-server.jar"                  : "1.6",
    "plugins/maven/lib/maven3-server-common.jar"                : "1.6",
    "plugins/maven/lib/maven30-server.jar"                 : "1.6",
    "plugins/maven/lib/maven3-server.jar"                  : "1.6",
    "plugins/maven/lib/artifact-resolver-m2.jar"                : "1.6",
    "plugins/maven/lib/artifact-resolver-m3.jar"                : "1.6",
    "plugins/maven/lib/artifact-resolver-m31.jar"               : "1.6",
    "plugins/xpath/lib/rt/xslt-rt.jar"                          : "1.6",
    "plugins/xslt-debugger/lib/xslt-debugger-rt.jar"            : "1.6",
    "plugins/xslt-debugger/lib/rt/xslt-debugger-impl-rt.jar"    : "1.8",
    "plugins/android/lib/layoutlib-27.2.0.0.jar"                : "9",
  ]

  BaseIdeaProperties() {
    productLayout.mainJarName = "idea.jar"

    productLayout.additionalPlatformJars.put("resources.jar", "intellij.java.ide.resources")

    productLayout.platformLayoutCustomizer = { PlatformLayout layout ->
      layout.customize {
        for (String name : JAVA_IDE_API_MODULES) {
          withModule(name)
        }
        for (String name : JAVA_IDE_IMPLEMENTATION_MODULES) {
          withModule(name)
        }

        //todo currently intellij.platform.testFramework included into idea.jar depends on this jar so it cannot be moved to java plugin
        withModule("intellij.java.rt", "idea_rt.jar")

        //for compatibility with users' projects which take these libraries from IDEA installation
        withProjectLibrary("jetbrains-annotations")
        removeVersionFromProjectLibraryJarNames("jetbrains-annotations")
        withProjectLibrary("JUnit3")
        removeVersionFromProjectLibraryJarNames("JUnit3") //for compatibility with users projects which refer to IDEA_HOME/lib/junit.jar
        withProjectLibrary("commons-net")

        withoutProjectLibrary("Ant")

        // there is a patched version of the org.gradle.api.JavaVersion class placed into the Gradle plugin classpath as "rt" jar
        // to avoid class linkage conflicts "Gradle" library is placed into the 'lib' directory of the Gradle plugin layout so we need to exclude it from the platform layout explicitly
        // TODO should be used as regular project library when the issue will be fixed at the Gradle tooling api side https://github.com/gradle/gradle/issues/8431 and the patched class will be removed
        withoutProjectLibrary("Gradle")

        //this library is placed into subdirectory of 'lib' directory in Android plugin layout so we need to exclude it from the platform layout explicitly
        withoutProjectLibrary("layoutlib")
      }
    } as Consumer<PlatformLayout>

    productLayout.compatiblePluginsToIgnore = [
      "intellij.java.plugin",
      "kotlin.idea"
    ]
    additionalModulesToCompile = ["intellij.tools.jps.build.standalone"]
    modulesToCompileTests = ["intellij.platform.jps.build"]

    isAntRequired = true
  }

  @Override
  List<Path> getAdditionalPluginPaths(@NotNull BuildContext context) {
    return [context.kotlinBinaries.setUpPlugin(context)]
  }

  @Override
  @CompileStatic(TypeCheckingMode.SKIP)
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    context.ant.jar(destfile: "$targetDirectory/lib/jdkAnnotations.jar") {
      fileset(dir: "$context.paths.communityHome/java/jdkAnnotations")
    }

    if (isAntRequired) {
      context.ant.copy(todir: "$targetDirectory/lib/ant") {
        fileset(dir: "$context.paths.communityHome/lib/ant") {
          exclude(name: "**/src/**")
        }
      }
    }

    Path targetDir = Paths.get(targetDirectory).toAbsolutePath().normalize()

    Path java8AnnotationsJar = targetDir.resolve("lib/annotations.jar")
    BuildHelper.moveFile(java8AnnotationsJar, targetDir.resolve("redist/annotations-java8.jar"))
    // for compatibility with users projects which refer to IDEA_HOME/lib/annotations.jar
    BuildHelper.moveFile(targetDir.resolve("lib/annotations-java5.jar"), java8AnnotationsJar)
  }
}
