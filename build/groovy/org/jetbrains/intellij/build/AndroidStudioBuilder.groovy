/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build

import org.codehaus.gant.GantBinding
import org.jetbrains.jps.gant.JpsGantProjectBuilder
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsProject

/**
 * Based on IdeaCommunityBuilder, but simplified a bit since we build fewer things
 * (for example, no intellij-core distribution)
 */
class AndroidStudioBuilder {
  private final GantBinding binding
  private final BuildContext buildContext

  AndroidStudioBuilder(String home, GantBinding binding, BuildOptions options = new BuildOptions(), String projectHome = home) {
    this(home, binding, binding.ant, binding.projectBuilder, binding.project, binding.global, options, projectHome)
  }

  AndroidStudioBuilder(String home, GantBinding binding, AntBuilder ant, JpsGantProjectBuilder projectBuilder, JpsProject project,
                       JpsGlobal global, BuildOptions options = new BuildOptions(), String projectHome = home) {
    this.binding = binding
    buildContext = BuildContext.createContext(ant, projectBuilder, project, global, home, projectHome,
                                              new AndroidStudioProperties(home), ProprietaryBuildTools.DUMMY,
                                              options)
  }

  void compileModules() {
    BuildTasks.create(buildContext).compileProjectAndTests(["jps-builders"])
  }

  void buildDistJars() {
    BuildTasks.create(buildContext).buildDistributions()
    layoutAdditionalArtifacts()
  }

  void buildDistributions(boolean additional = true) {
    def tasks = BuildTasks.create(buildContext)
    tasks.buildDistributions()
    if (additional) {
      layoutAdditionalArtifacts(true)
    }
    tasks.buildUpdaterJar()
  }

  void layoutAdditionalArtifacts(boolean buildJps = false) {
    def layouts = binding["includeFile"]("$buildContext.paths.communityHome/build/scripts/layouts.gant")
    layoutIntelliJCore(layouts)
    if (buildJps) {
      buildContext.messages.block("Build standalone JPS") {
        String jpsArtifactDir = "$buildContext.paths.artifacts/jps"
        layouts.layoutJps(buildContext.paths.communityHome, jpsArtifactDir, buildContext.fullBuildNumber, {})
        buildContext.notifyArtifactBuilt(jpsArtifactDir)
      }
    }
  }

  private void layoutIntelliJCore(layouts) {
    buildContext.messages.block("Build intellij-core") {
      String coreArtifactDir = "$buildContext.paths.artifacts/core"
      buildContext.ant.mkdir(dir: coreArtifactDir)
      layouts.layout_core(buildContext.paths.communityHome, coreArtifactDir)
      buildContext.notifyArtifactBuilt(coreArtifactDir)

      def intellijCoreZip = "${buildContext.paths.artifacts}/intellij-core-${buildContext.buildNumber}.zip"
      buildContext.ant.zip(destfile: intellijCoreZip) {
        fileset(dir: coreArtifactDir)
      }
      buildContext.notifyArtifactBuilt(intellijCoreZip)
    }
  }
}