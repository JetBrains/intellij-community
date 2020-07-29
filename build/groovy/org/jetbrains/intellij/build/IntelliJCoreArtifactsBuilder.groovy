// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.ClassVersionChecker
import org.jetbrains.intellij.build.impl.LayoutBuilder
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping

/**
 * Builds artifacts which are used in Kotlin Compiler and UpSource
 *
 * @deprecated all modules included into these artifacts are published as proper Maven artifacts to IntelliJ Artifacts Repository (http://www.jetbrains.org/intellij/sdk/docs/reference_guide/intellij_artifacts.html).
 */
@Deprecated
class IntelliJCoreArtifactsBuilder {
  private static final List<String> ANALYSIS_API_MODULES = [
    "intellij.platform.analysis",
    "intellij.platform.boot",
    "intellij.platform.core",
    "intellij.platform.duplicates.analysis",
    "intellij.platform.editor",
    "intellij.platform.editor.ex",
    "intellij.platform.extensions",
    "intellij.platform.indexing",
    "intellij.java.analysis",
    "intellij.java.indexing",
    "intellij.java.psi",
    "intellij.java.structureView",
    "intellij.platform.jps.model",
    "intellij.platform.jps.model.serialization",
    "intellij.platform.projectModel",
    "intellij.platform.util",
    "intellij.platform.util.rt",
    "intellij.platform.util.text.matching",
    "intellij.platform.util.collections",
    "intellij.platform.util.strings",
    "intellij.platform.util.diagnostic",
    "intellij.platform.util.classLoader",
    "intellij.xml.analysis",
    "intellij.xml.psi",
    "intellij.xml.structureView",
    "intellij.jvm.analysis",
  ]
  private static final List<String> ANALYSIS_IMPL_MODULES = [
    "intellij.platform.analysis.impl",
    "intellij.platform.core.impl",
    "intellij.platform.indexing.impl",
    "intellij.java.analysis.impl",
    "intellij.java.indexing.impl",
    "intellij.java.psi.impl",
    "intellij.platform.projectModel.impl",
    "intellij.platform.structureView.impl",
    "intellij.xml.analysis.impl",
    "intellij.xml.psi.impl",
    "intellij.xml.structureView.impl",
    "intellij.jvm.analysis.impl",
  ]
  private static final List<String> VERSIONED_LIBRARIES = [
    "ASM", "Guava", "Trove4j", "cli-parser", "lz4-java",
    "OroMatcher", "jna", "Log4J", "StreamEx"
  ]
  private static final List<String> UNVERSIONED_LIBRARIES = [
    "jetbrains-annotations-java5", "JDOM"
  ]

  private final BuildContext buildContext

  IntelliJCoreArtifactsBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  void compileModules() {
    BuildTasks.create(buildContext).compileModules(ANALYSIS_API_MODULES + ANALYSIS_IMPL_MODULES)
  }

  void layoutIntelliJCore() {
    buildContext.messages.block("Build intellij-core") {
      String coreArtifactDir = "$buildContext.paths.artifacts/core"
      AntBuilder ant = buildContext.ant
      ant.mkdir(dir: coreArtifactDir)

      ant.echo(message: "These artifacts are deprecated, use artifacts from IntelliJ Artifacts Repository (http://www.jetbrains.org/intellij/sdk/docs/reference_guide/intellij_artifacts.html) instead",
               file: "$coreArtifactDir/README.txt")
      processCoreLayout(coreArtifactDir, new ProjectStructureMapping(), true)
      ant.move(file: "$coreArtifactDir/annotations-java5.jar", tofile: "$coreArtifactDir/annotations.jar")
      buildContext.notifyArtifactBuilt(coreArtifactDir)

      new ClassVersionChecker(["": "1.8"]).checkVersions(buildContext, new File(coreArtifactDir))

      def intellijCoreZip = "${buildContext.paths.artifacts}/intellij-core-${buildContext.buildNumber}.zip"
      ant.zip(destfile: intellijCoreZip) {
        fileset(dir: coreArtifactDir)
      }
      buildContext.notifyArtifactBuilt(intellijCoreZip)
    }
  }

  void generateProjectStructureMapping(File targetFile) {
    def mapping = new ProjectStructureMapping()
    processCoreLayout(buildContext.paths.temp, mapping, false)
    mapping.addEntry(new ProjectLibraryEntry("annotations.jar", "jetbrains-annotations-java5"))
    mapping.generateJsonFile(targetFile)
  }

  private void processCoreLayout(String coreArtifactDir, ProjectStructureMapping projectStructureMapping, boolean copyFiles) {
    List<String> analysisModules = ANALYSIS_API_MODULES + ANALYSIS_IMPL_MODULES
    List<String> versionedLibs = VERSIONED_LIBRARIES
    List<String> unversionedLibs = UNVERSIONED_LIBRARIES
    new LayoutBuilder(buildContext, false).process(coreArtifactDir, projectStructureMapping, copyFiles) {
      jar("intellij-core.jar") {
        module("intellij.platform.util.rt")
        module("intellij.platform.util.classLoader")
        module("intellij.platform.util.text.matching")
        module("intellij.platform.util.collections")
        module("intellij.platform.util.strings")
        module("intellij.platform.util.diagnostic")
        module("intellij.platform.util")
        module("intellij.platform.core")
        module("intellij.platform.core.impl")
        module("intellij.platform.extensions")
        module("intellij.java.psi")
        module("intellij.java.psi.impl")
      }
      jar("intellij-core-analysis-deprecated.jar") {
        analysisModules.each { module it }
      }
      versionedLibs.each { projectLibrary(it) }
      unversionedLibs.each { projectLibrary(it, true) }
    }
  }
}