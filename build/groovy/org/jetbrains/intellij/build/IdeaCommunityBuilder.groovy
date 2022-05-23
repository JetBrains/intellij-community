// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping

import java.nio.file.Path

@CompileStatic
final class IdeaCommunityBuilder {
  private final BuildContext context

  IdeaCommunityBuilder(Path home, BuildOptions options = new BuildOptions(), Path projectHome = home) {
    this(BuildContextImpl.createContext(home, projectHome, new IdeaCommunityProperties(home), ProprietaryBuildTools.DUMMY, options))
  }

  IdeaCommunityBuilder(BuildContext context) {
    this.context = context
  }

  /**
   * Compiles production modules and test modules required for {@link org.jetbrains.intellij.build.CommunityStandaloneJpsBuilder#processJpsLayout}
   */
  void compileModules() {
    BuildTasks.create(context).compileProjectAndTests(["intellij.platform.jps.build"])
  }

  void buildFullUpdater() {
    def tasks = BuildTasks.create(context)
    tasks.compileModules(List.of("updater"))
    tasks.buildFullUpdaterJar()
  }

  void buildDistributions() {
    compileModules()
    /**
     * required because {@link org.jetbrains.intellij.build.BuildTasks#buildDistributions} will trigger compilation of production modules
     * wiping out test modules compiled in {@link org.jetbrains.intellij.build.IdeaCommunityBuilder#compileModules}
     */
    context.options.incrementalCompilation = true
    def tasks = BuildTasks.create(context)
    tasks.buildDistributions()
    context.messages.block("Build standalone JPS") {
      Path jpsArtifactDir = context.paths.artifactDir.resolve("jps")
      new CommunityStandaloneJpsBuilder(context)
        .processJpsLayout(jpsArtifactDir, context.fullBuildNumber, new ProjectStructureMapping(), true, {})
    }
  }

  void buildUnpackedDistribution(Path targetDirectory) {
    BuildTasks.create(context).buildUnpackedDistribution(targetDirectory, false)
  }
}
