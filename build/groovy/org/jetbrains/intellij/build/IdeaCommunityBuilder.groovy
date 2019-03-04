// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

/**
 * @author nik
 */
@CompileStatic
class IdeaCommunityBuilder {
  private final BuildContext buildContext

  IdeaCommunityBuilder(String home, BuildOptions options = new BuildOptions(), String projectHome = home) {
    buildContext = BuildContext.createContext(home, projectHome, new IdeaCommunityProperties(home), ProprietaryBuildTools.DUMMY, options)
  }

  IdeaCommunityBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  void compileModules() {
    BuildTasks.create(buildContext).compileProjectAndTests(["intellij.platform.jps.build"])
  }

  void buildFullUpdater() {
    def tasks = BuildTasks.create(buildContext)
    tasks.compileModules(["updater"])
    tasks.buildFullUpdaterJar()
  }

  void buildIntelliJCore(boolean compileModules = true) {
    def builder = new IntelliJCoreArtifactsBuilder(buildContext)
    if (compileModules) {
      builder.compileModules()
    }
    builder.layoutIntelliJCore()
  }

  void buildDistributions() {
    def tasks = BuildTasks.create(buildContext)
    tasks.buildDistributions()
    buildContext.messages.block("Build standalone JPS") {
      String jpsArtifactDir = "$buildContext.paths.artifacts/jps"
      new CommunityStandaloneJpsBuilder(buildContext).layoutJps(jpsArtifactDir, buildContext.fullBuildNumber, {})
    }
    tasks.buildUpdaterJar()
  }

  void buildUnpackedDistribution(String targetDirectory) {
    BuildTasks.create(buildContext).buildUnpackedDistribution(targetDirectory)
  }
}