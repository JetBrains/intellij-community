// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.ProjectLibraryData

import java.util.function.Consumer

@CompileStatic
final class JavaPluginLayout {
  static PluginLayout javaPlugin(@DelegatesTo(PluginLayout.PluginLayoutSpec) Consumer<PluginLayout.PluginLayoutSpec> addition = null) {
    return PluginLayout.plugin("intellij.java.plugin", new Consumer<PluginLayout.PluginLayoutSpec>() {
      @Override
      void accept(PluginLayout.PluginLayoutSpec spec) {
        spec.directoryName = "java"

        String mainJarName = "java-impl.jar"
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

        for (String moduleName in List.of(
          "intellij.java.compiler.antTasks",
          "intellij.java.guiForms.compiler",
          "intellij.java.compiler.instrumentationUtil",
          "intellij.java.compiler.instrumentationUtil.java8"
        )) {
          spec.withModule(moduleName, "javac2.jar")
        }

        // api modules
        for (String moduleName in List.of(
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
        )) {
          spec.withModule(moduleName, mainJarName)
        }

        for (String moduleName in List.of(
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
        )) {
          spec.withModule(moduleName, mainJarName)
        }

        spec.withArtifact("debugger-agent", "rt")
        spec.layout.includedProjectLibraries.add(new ProjectLibraryData("Eclipse", "ecj", ProjectLibraryData.PackMode.STANDALONE_MERGED))
        // used in JPS - do not use uber jar
        spec.withProjectLibrary("jgoodies-common", ProjectLibraryData.PackMode.STANDALONE_MERGED)
        spec.withProjectLibrary("jps-javac-extension", ProjectLibraryData.PackMode.STANDALONE_MERGED)
        // gpl-cpe license - do not use uber jar
        spec.withProjectLibrary("jb-jdi", ProjectLibraryData.PackMode.STANDALONE_MERGED)

        spec.withModuleLibrary("debugger-memory-agent", "intellij.java.debugger.memory.agent", "")
        // explicitly pack jshell-frontend and sa-jdwp as a separate JARs
        spec.withModuleLibrary("jshell-frontend", "intellij.java.execution.impl", "jshell-frontend.jar")
        spec.withModuleLibrary("sa-jdwp", "intellij.java.debugger.impl", "sa-jdwp.jar")

        spec.withResourceArchive("../jdkAnnotations", "lib/resources/jdkAnnotations.jar")

        if (addition != null) {
          addition.accept(spec)
        }
      }
    })
  }
}
