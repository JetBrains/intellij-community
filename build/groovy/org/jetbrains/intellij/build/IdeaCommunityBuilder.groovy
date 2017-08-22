/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build
/**
 * @author nik
 */
class IdeaCommunityBuilder {
  private final BuildContext buildContext

  IdeaCommunityBuilder(String home, BuildOptions options = new BuildOptions(), String projectHome = home) {
    buildContext = BuildContext.createContext(home, projectHome, new IdeaCommunityProperties(home), ProprietaryBuildTools.DUMMY, options)
  }

  IdeaCommunityBuilder(BuildContext buildContext) {
    this.buildContext = buildContext
  }

  void compileModules() {
    BuildTasks.create(buildContext).compileProjectAndTests(["jps-builders"])
  }

  void buildIntelliJCore() {
    def builder = new IntelliJCoreArtifactsBuilder(buildContext)
    builder.compileModules()
    builder.layoutIntelliJCore()
  }

  void buildDistJars() {
    BuildTasks.create(buildContext).buildDistributions()
    layoutCoreArtifacts()
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

  void layoutCoreArtifacts() {
    new IntelliJCoreArtifactsBuilder(buildContext).layoutIntelliJCore()
  }
}