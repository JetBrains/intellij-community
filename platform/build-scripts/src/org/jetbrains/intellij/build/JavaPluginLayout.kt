// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.LibraryPackMode
import org.jetbrains.intellij.build.impl.PluginLayout

object JavaPluginLayout {
  @JvmStatic
  @JvmOverloads
  fun javaPlugin(addition: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    return PluginLayout.plugin("intellij.java.plugin") { spec ->
      spec.directoryName = "java"

      val mainJarName = "java-impl.jar"
      spec.mainJarName = mainJarName

      spec.excludeFromModule("intellij.java.resources.en", "search/searchableOptions.xml")

      spec.withModule("intellij.platform.jps.build.launcher", "jps-launcher.jar")
      spec.withModule("intellij.platform.jps.build", "jps-builders.jar")
      spec.withModule("intellij.platform.jps.build.javac.rt", "jps-builders-6.jar")
      spec.withModule("intellij.java.aetherDependencyResolver", "aether-dependency-resolver.jar")
      spec.withModule("intellij.java.jshell.protocol", "jshell-protocol.jar")
      spec.withModule("intellij.java.resources", mainJarName)
      spec.withModule("intellij.java.resources.en", mainJarName)

      // JavacRemoteProto generated against protobuf-java6; don't let it sneak into the IDE classpath and shadow its JavacRemoteProto.
      spec.withModule("intellij.platform.jps.build.javac.rt.rpc", "rt/jps-javac-rt-rpc.jar")
      spec.withModuleLibrary("protobuf-java6", "intellij.platform.jps.build.javac.rt.rpc", "rt")

      for (moduleName in listOf(
        "intellij.java.compiler.antTasks",
        "intellij.java.guiForms.compiler",
        "intellij.java.compiler.instrumentationUtil",
        "intellij.java.compiler.instrumentationUtil.java8"
      )) {
        spec.withModule(moduleName, "javac2.jar")
      }

      // api modules
      spec.withModules(listOf(
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
      ))

      spec.withModules(listOf(
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
        "intellij.java.impl.refactorings",
        "intellij.jsp.spi",
        "intellij.java.uast",
        "intellij.java.structuralSearch",
        "intellij.java.typeMigration",
        "intellij.java.featuresTrainer"
      ))

      spec.withArtifact("debugger-agent", "rt")
      spec.withProjectLibrary("Eclipse", "ecj", LibraryPackMode.STANDALONE_MERGED)
      // used in JPS - do not use uber jar
      spec.withProjectLibrary("jgoodies-common", LibraryPackMode.STANDALONE_MERGED)
      spec.withProjectLibrary("jps-javac-extension", LibraryPackMode.STANDALONE_MERGED)
      // gpl-cpe license - do not use uber jar
      spec.withProjectLibrary("jb-jdi", LibraryPackMode.STANDALONE_MERGED)

      spec.withModuleLibrary("debugger-memory-agent", "intellij.java.debugger.memory.agent", "")
      // explicitly pack jshell-frontend and sa-jdwp as a separate JARs
      spec.withModuleLibrary("jshell-frontend", "intellij.java.execution.impl", "jshell-frontend.jar")
      spec.withModuleLibrary("sa-jdwp", "intellij.java.debugger.impl", "sa-jdwp.jar")

      spec.withResourceArchive("../jdkAnnotations", "lib/resources/jdkAnnotations.jar")

      addition?.invoke(spec)
    }
  }
}
