// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.PluginLayout

class JavaPluginLayout {
  public static final PluginLayout JAVA_PLUGIN = PluginLayout.plugin("intellij.java.plugin") {
    directoryName = "java"
    mainJarName = "java-impl.jar"
    excludeFromModule("intellij.java.resources.en", "search/searchableOptions.xml")
    withModule("intellij.platform.externalSystem.rt", "external-system-rt.jar")
    withModule("intellij.platform.externalSystem.impl", "external-system-impl.jar")
    withModule("intellij.platform.jps.build.launcher", "jps-launcher.jar")
    withModule("intellij.platform.jps.build", "jps-builders.jar")
    withModule("intellij.platform.jps.build.javac.rt", "jps-builders-6.jar")
    withModule("intellij.java.aetherDependencyResolver", "aether-dependency-resolver.jar")
    withModule("intellij.java.jshell.protocol", "jshell-protocol.jar")
    withModule("intellij.java.resources", "resources.jar")
    withModule("intellij.java.resources.en", "resources.jar")

    ["intellij.java.compiler.antTasks", "intellij.java.guiForms.compiler", "intellij.java.guiForms.rt", "intellij.java.compiler.instrumentationUtil", "intellij.java.compiler.instrumentationUtil.java8", "intellij.java.jps.javacRefScanner8"].each {
      withModule(it, "javac2.jar")
    }

    [
      "intellij.java.compiler",
      "intellij.java.debugger",
      "intellij.java.execution",
      "intellij.java.remoteServers",
      "intellij.java.analysis",
      "intellij.jvm.analysis",
      "intellij.java.indexing",
      "intellij.java.psi",
      "intellij.java",
      "intellij.jsp.base",
      "intellij.jsp",
      "intellij.platform.uast"
    ].each {
      withModule(it, "java-api.jar", "java_resources_en.jar")
    }
    [
      "intellij.java.compiler.impl",
      "intellij.java.debugger.impl",
      "intellij.java.debugger.memory.agent",
      "intellij.java.execution.impl",
      "intellij.java.ui",
      "intellij.java.structureView",
      "intellij.java.manifest",
      "intellij.java.remoteServers.impl",
      "intellij.uiDesigner",
      "intellij.java.analysis.impl",
      "intellij.jvm.analysis.impl",
      "intellij.java.indexing.impl",
      "intellij.java.psi.impl",
      "intellij.java.impl",
      "intellij.jsp.spi",
      "intellij.java.uast",
      "intellij.java.structuralSearch",
      "intellij.java.typeMigration"
    ].each {
      withModule(it, "java-impl.jar", "java_resources_en.jar")
    }

    // externalSystem API is coupled with `java` modules and therefore must be included into the same plugin
    // TODO[Vlad, IDEA-187832] remove this from Java plugin distribution as soon as the externalSystem API become java subsystem independent platform API
    withModule("intellij.platform.externalSystem")
    withModule("intellij.platform.externalSystem.impl")
    withModule("intellij.platform.externalSystem.rt")

    withModule("intellij.java.rt", "idea_rt.jar", null)
    withArtifact("debugger-agent", "rt")
    withArtifact("debugger-agent-storage", "rt")
    withProjectLibrary("Eclipse")
    withProjectLibrary("jgoodies-common")
    withProjectLibrary("commons-net")
    withProjectLibrary("snakeyaml")
    withProjectLibrary("commons-io")
    withProjectLibrary("maven-model")
    withProjectLibrary("debugger-memory-agent")//todo nik: convert to module-level library instead

    withResourceArchive("../jdkAnnotations", "lib/jdkAnnotations.jar")
    withGeneratedResources(new ResourcesGenerator() {
      @Override
      File generateResources(BuildContext context) {
        return new File(context.paths.jdkHome, "lib/tools.jar")
      }
    }, "lib")
  }
}
