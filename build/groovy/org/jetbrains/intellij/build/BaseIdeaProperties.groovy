// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.PlatformLayout

import java.util.function.Consumer

/**
 * @author nik
 */
abstract class BaseIdeaProperties extends ProductProperties {
  public static final List<String> JAVA_IDE_API_MODULES = [
    "intellij.xml.dom",
    "intellij.java.testFramework",
    "intellij.platform.testFramework.core",
    "intellij.platform.uast.tests"
  ]
  public static final List<String> JAVA_IDE_IMPLEMENTATION_MODULES = [
    "intellij.xml.dom.impl",
    "intellij.platform.testFramework",
    "intellij.tools.testsBootstrap"
  ]
  protected static final List<String> BUNDLED_PLUGIN_MODULES = [
    "intellij.java.plugin",
    "intellij.copyright",
    "intellij.properties",
    "intellij.terminal",
    "intellij.textmate",
    "intellij.editorconfig",
    "intellij.settingsRepository",
    "intellij.configurationScript",
    "intellij.yaml",
    "intellij.tasks.core",
    "intellij.maven",
    "intellij.gradle",
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
    "intellij.statsCollector",
    "intellij.sh",
    "intellij.vcs.changeReminder",
    "intellij.markdown"
  ]
  protected static final Map<String, String> CE_CLASS_VERSIONS = [
    "": "1.8",
    "lib/idea_rt.jar": "1.3",
    "lib/forms_rt.jar": "1.4",
    "lib/annotations.jar": "1.5",
    "lib/util.jar": "1.8",
    "lib/external-system-rt.jar": "1.6",
    "lib/jshell-frontend.jar": "1.9",
    "lib/sa-jdwp": "",  // ignored
    "plugins/java/lib/rt/debugger-agent.jar": "1.6",
    "plugins/java/lib/rt/debugger-agent-storage.jar": "1.6",
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

    //for compatibility with generated Ant build.xml files which refer to this file
    productLayout.additionalPlatformJars.
      putAll("javac2.jar",
             ["intellij.java.compiler.antTasks", "intellij.java.guiForms.compiler", "intellij.java.guiForms.rt",
              "intellij.java.compiler.instrumentationUtil", "intellij.java.compiler.instrumentationUtil.java8",
              "intellij.java.jps.javacRefScanner8"])

    productLayout.additionalPlatformJars.put("resources.jar", "intellij.java.ide.resources")

    productLayout.platformLayoutCustomizer = { PlatformLayout layout ->
      layout.customize {
        //todo currently intellij.platform.testFramework included into idea.jar depends on this jar so it cannot be moved to java plugin
        withModule("intellij.java.rt", "idea_rt.jar", null)

        //for compatibility with users' projects which take these libraries from IDEA installation
        withProjectLibrary("jetbrains-annotations")
        removeVersionFromProjectLibraryJarNames("jetbrains-annotations")
        withProjectLibrary("JUnit3")
        removeVersionFromProjectLibraryJarNames("JUnit3") //for compatibility with users projects which refer to IDEA_HOME/lib/junit.jar
        withProjectLibrary("commons-net")

        withoutProjectLibrary("Ant")
        withoutProjectLibrary("Gradle")
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