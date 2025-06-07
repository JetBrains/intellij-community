// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.PluginLayout

object JavaPluginLayout {
  const val MAIN_MODULE_NAME = "intellij.java.plugin"

  fun javaPlugin(addition: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    return PluginLayout.plugin(mainModuleName = MAIN_MODULE_NAME, auto = true) { spec ->
      spec.directoryName = "java"
      spec.mainJarName = "java-impl.jar"

      spec.withProjectLibrary("netty-jps", "rt/netty-jps.jar")

      spec.withModule("intellij.platform.jps.build.launcher", "jps-launcher.jar")
      for (moduleName in listOf(
        "intellij.platform.jps.build",
        "intellij.platform.jps.build.dependencyGraph",
      )) {
        spec.withModule(moduleName, "jps-builders.jar")
      }
      spec.withModule("intellij.platform.jps.build.javac.rt", "jps-builders-6.jar")
      spec.withModule("intellij.java.aetherDependencyResolver", "aether-dependency-resolver.jar")
      spec.withModule("intellij.java.jshell.protocol", "jshell-protocol.jar")

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
        "intellij.java.execution.impl.shared",
        "intellij.java.remoteServers",
        "intellij.java.analysis",
        "intellij.jvm.analysis",
        "intellij.java.indexing",
        "intellij.java",
        "intellij.jsp",
        "intellij.platform.uast",
        "intellij.platform.uast.ide",
        "intellij.java.uast.ide",
      ))

      spec.withModules(listOf(
        "intellij.java.codeserver.core",
        "intellij.java.codeserver.highlighting",
        "intellij.java.compiler.impl",
        "intellij.java.debugger.impl",
        "intellij.java.terminal",
        "intellij.java.debugger.memory.agent",
        "intellij.java.execution.impl",
        "intellij.java.execution.impl.backend",
        "intellij.java.execution.impl.frontend",
        "intellij.java.ui",
        "intellij.java.structureView",
        "intellij.java.manifest",
        "intellij.java.remoteServers.impl",
        "intellij.uiDesigner",
        "intellij.java.analysis.impl",
        "intellij.jvm.analysis.impl",
        "intellij.jvm.analysis.quickFix",
        "intellij.jvm.analysis.refactoring",
        "intellij.java.indexing.impl",
        "intellij.java.impl",
        "intellij.java.impl.inspections",
        "intellij.java.impl.refactorings",
        "intellij.jsp.spi",
        "intellij.java.uast",
        "intellij.java.typeMigration",
      ))

      spec.withModuleLibrary("debugger-agent", "intellij.java.debugger.agent.holder", "rt")

      spec.withProjectLibrary("Eclipse", "ecj")
      // used in JPS - do not use uber jar
      spec.withProjectLibrary("jgoodies-common")
      spec.withProjectLibrary("jps-javac-extension")
      spec.withProjectLibrary("kotlin-metadata")
      // gpl-cpe license - do not use uber jar
      spec.withProjectLibrary("jb-jdi")

      spec.withModuleLibrary("debugger-memory-agent", "intellij.java.debugger.memory.agent", "")
      // explicitly pack jshell-frontend and sa-jdwp as a separate JARs
      spec.withModuleLibrary("jshell-frontend", "intellij.java.execution.impl", "jshell-frontend.jar")
      spec.withModuleLibrary("sa-jdwp", "intellij.java.debugger.impl", "sa-jdwp.jar")

      spec.withResourceArchive("../jdkAnnotations", "lib/resources/jdkAnnotations.jar")

      addition?.invoke(spec)

      spec.excludeProjectLibrary("jetbrains-annotations-java5")
    }
  }
}
