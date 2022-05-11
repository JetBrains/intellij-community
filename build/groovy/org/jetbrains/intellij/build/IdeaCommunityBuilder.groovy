// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping

import java.nio.file.Path

@CompileStatic
final class IdeaCommunityBuilder {
  private final BuildContext buildContext

  IdeaCommunityBuilder(Path home, BuildOptions options = new BuildOptions(), Path projectHome = home) {
    this(BuildContextImpl.createContext(home, projectHome, new IdeaCommunityProperties(home), ProprietaryBuildTools.DUMMY, options))
  }

  IdeaCommunityBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  /**
   * Compiles production modules and test modules required for {@link org.jetbrains.intellij.build.CommunityStandaloneJpsBuilder#processJpsLayout}
   */
  void compileModules() {
    BuildTasks.create(buildContext).compileProjectAndTests(["intellij.platform.jps.build"])
  }

  void buildFullUpdater() {
    def tasks = BuildTasks.create(buildContext)
    tasks.compileModules(List.of("updater"))
    tasks.buildFullUpdaterJar()
  }

  void buildDistributions() {
    compileModules()
    /**
     * required because {@link org.jetbrains.intellij.build.BuildTasks#buildDistributions} will trigger compilation of production modules
     * wiping out test modules compiled in {@link org.jetbrains.intellij.build.IdeaCommunityBuilder#compileModules}
     */
    buildContext.options.incrementalCompilation = true
    def tasks = BuildTasks.create(buildContext)
    tasks.buildDistributions()
    buildContext.messages.block("Build standalone JPS") {
      Path jpsArtifactDir = buildContext.paths.artifactDir.resolve("jps")
      new CommunityStandaloneJpsBuilder(buildContext)
        .processJpsLayout(jpsArtifactDir, buildContext.fullBuildNumber, new ProjectStructureMapping(), true, {})
    }
  }

  void buildUnpackedDistribution(Path targetDirectory) {
    BuildTasks.create(buildContext).buildUnpackedDistribution(targetDirectory, false)
  }
}
