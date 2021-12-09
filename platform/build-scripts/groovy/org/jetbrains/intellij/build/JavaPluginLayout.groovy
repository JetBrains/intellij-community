// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build


import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.ProjectLibraryData

final class JavaPluginLayout {
  static PluginLayout javaPlugin(@DelegatesTo(PluginLayout.PluginLayoutSpec) Closure addition = {}) {
    return PluginLayout.plugin("intellij.java.plugin") {
      directoryName = "java"
      mainJarName = "java-impl.jar"

      excludeFromModule("intellij.java.resources.en", "search/searchableOptions.xml")

      withModule("intellij.platform.jps.build.launcher", "jps-launcher.jar")
      withModule("intellij.platform.jps.build", "jps-builders.jar")
      withModule("intellij.platform.jps.build.javac.rt", "jps-builders-6.jar")
      withModule("intellij.java.aetherDependencyResolver", "aether-dependency-resolver.jar")
      withModule("intellij.java.jshell.protocol", "jshell-protocol.jar")
      withModule("intellij.java.resources", mainJarName)
      withModule("intellij.java.resources.en", mainJarName)

      // JavacRemoteProto generated against protobuf-java6; don't let it sneak into the IDE classpath and shadow its JavacRemoteProto.
      withModule("intellij.platform.jps.build.javac.rt.rpc", "rt/jps-javac-rt-rpc.jar")
      withModuleLibrary("protobuf-java6", "intellij.platform.jps.build.javac.rt.rpc", "rt")
      // used by JPS, cannot be packed into 3rd-party.jar
      //noinspection SpellCheckingInspection
      withModuleLibrary("qdox-java-parser", "intellij.platform.jps.build", "qdox.jar")

      ["intellij.java.compiler.antTasks",
       "intellij.java.guiForms.compiler",
       "intellij.java.compiler.instrumentationUtil",
       "intellij.java.compiler.instrumentationUtil.java8"
      ].each {
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
        "intellij.jsp",
        "intellij.platform.uast"
      ].each {
        withModule(it, "java-api.jar")
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
        "intellij.java.impl.inspections",
        "intellij.jsp.spi",
        "intellij.java.uast",
        "intellij.java.structuralSearch",
        "intellij.java.typeMigration",
        "intellij.java.featuresTrainer"
      ].each {
        withModule(it, mainJarName)
      }

      withArtifact("debugger-agent", "rt")
      layout.includedProjectLibraries.add(new ProjectLibraryData("Eclipse", "ecj", ProjectLibraryData.PackMode.STANDALONE_MERGED))
      withProjectLibrary("jgoodies-common")
      withProjectLibrary("jps-javac-extension")
      withProjectLibrary("jb-jdi")

      withModuleLibrary("debugger-memory-agent", "intellij.java.debugger.memory.agent", "")
      // explicitly pack jshell-frontend and sa-jdwp as a separate JARs
      withModuleLibrary("jshell-frontend", "intellij.java.execution.impl", "jshell-frontend.jar")
      withModuleLibrary("sa-jdwp", "intellij.java.debugger.impl", "sa-jdwp.jar")

      withResourceArchive("../jdkAnnotations", "lib/resources/jdkAnnotations.jar")

      addition.delegate = delegate
      addition()
    }
  }
}
