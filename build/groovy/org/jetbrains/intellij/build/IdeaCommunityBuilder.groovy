// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping

import java.nio.file.Paths

@CompileStatic
final class IdeaCommunityBuilder {
  private final BuildContext buildContext

  IdeaCommunityBuilder(String home, BuildOptions options = new BuildOptions(), String projectHome = home) {
    buildContext = BuildContext.createContext(home, projectHome, new IdeaCommunityProperties(home), ProprietaryBuildTools.DUMMY, options)
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
    tasks.compileModules(["updater"])
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
      String jpsArtifactDir = "$buildContext.paths.artifacts/jps"
      new CommunityStandaloneJpsBuilder(buildContext).processJpsLayout(jpsArtifactDir, buildContext.fullBuildNumber, new ProjectStructureMapping(),
                                                                       true, {})
    }
    tasks.buildUpdaterJar()
  }

  void buildUnpackedDistribution(String targetDirectory) {
    BuildTasks.create(buildContext).buildUnpackedDistribution(Paths.get(targetDirectory), false)
  }
}