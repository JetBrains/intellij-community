// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.impl.LayoutBuilder
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping

import java.nio.file.Files
import java.nio.file.Path
/**
 * Builds artifacts which are used in Kotlin Compiler and UpSource
 *
 * @deprecated all modules included into these artifacts are published as proper Maven artifacts to IntelliJ Artifacts Repository (http://www.jetbrains.org/intellij/sdk/docs/reference_guide/intellij_artifacts.html).
 */
@Deprecated
final class IntelliJCoreArtifactsBuilder {
  private static final List<String> VERSIONED_LIBRARIES = [
    "ASM", "Guava", "Trove4j", "cli-parser", "lz4-java",
    "OroMatcher", "jna", "Log4J", "StreamEx"
  ]
  private static final List<String> UNVERSIONED_LIBRARIES = [
    "jetbrains-annotations-java5", "JDOM"
  ]
  private static final List<String> CORE_MODULES = [
    "intellij.platform.util.rt",
    "intellij.platform.util.classLoader",
    "intellij.platform.util.text.matching",
    "intellij.platform.util.base",
    "intellij.platform.util.xmlDom",
    "intellij.platform.util",
    "intellij.platform.core",
    "intellij.platform.core.impl",
    "intellij.platform.extensions",
    "intellij.java.psi",
    "intellij.java.psi.impl",
  ]

  private final BuildContext buildContext

  IntelliJCoreArtifactsBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  void compileModules() {
    BuildTasks.create(buildContext).compileModules(CORE_MODULES)
  }

  void layoutIntelliJCore() {
    buildContext.messages.block("Build intellij-core") {
      Path coreArtifactDir = buildContext.paths.artifactDir.resolve("core")
      Files.createDirectories(coreArtifactDir)

      AntBuilder ant = buildContext.ant
      ant.echo(message: "These artifacts are deprecated, use artifacts from IntelliJ Artifacts Repository (http://www.jetbrains.org/intellij/sdk/docs/reference_guide/intellij_artifacts.html) instead",
               file: "$coreArtifactDir/README.txt")
      processCoreLayout(coreArtifactDir, new ProjectStructureMapping(), true)
      ant.move(file: "$coreArtifactDir/annotations-java5.jar", tofile: "$coreArtifactDir/annotations.jar")
      buildContext.notifyArtifactWasBuilt(coreArtifactDir)

      Path intellijCoreZip = buildContext.paths.artifactDir.resolve("intellij-core-${buildContext.buildNumber}.zip")
      ant.zip(destfile: intellijCoreZip.toString()) {
        fileset(dir: coreArtifactDir.toString())
      }
      buildContext.notifyArtifactWasBuilt(intellijCoreZip)
    }
  }

  void generateProjectStructureMapping(@NotNull File targetFile) {
    ProjectStructureMapping mapping = new ProjectStructureMapping()
    processCoreLayout(buildContext.paths.tempDir, mapping, false)
    mapping.addEntry(new ProjectLibraryEntry(Path.of("annotations.jar"), "jetbrains-annotations-java5", null, 0))
    mapping.generateJsonFile(targetFile.toPath(), buildContext.paths)
  }

  private void processCoreLayout(Path coreArtifactDir, ProjectStructureMapping projectStructureMapping, boolean copyFiles) {
    List<String> versionedLibs = VERSIONED_LIBRARIES
    List<String> unversionedLibs = UNVERSIONED_LIBRARIES
    List<String> coreModules = CORE_MODULES

    new LayoutBuilder(buildContext).process(coreArtifactDir.toString(), projectStructureMapping, copyFiles) {
      jar("intellij-core.jar") {
        coreModules.each { module(it) }
      }
      versionedLibs.each { projectLibrary(it) }
      unversionedLibs.each { projectLibrary(it, true) }
    }
  }
}