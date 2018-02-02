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

/**
 * Based on IdeaCommunityBuilder, but simplified a bit since we build fewer things
 * (for example, no intellij-core distribution)
 */
class AndroidStudioBuilder {
  private final GantBinding binding
  private final BuildContext buildContext

  AndroidStudioBuilder(String home, BuildOptions options = new BuildOptions(), String projectHome = home) {
    def properties = Boolean.getBoolean("include.aswb") ? new AswbProperties(home) : new AndroidStudioProperties(home)
    buildContext = BuildContext.createContext(home, projectHome, properties, ProprietaryBuildTools.DUMMY, options)
  }

  void compileModules() {
    BuildTasks.create(buildContext).compileProjectAndTests(["jps-builders"])
  }

  void buildDistJars() {
    BuildTasks.create(buildContext).buildDistributions()
    layoutCoreArtifacts()
  }

  void buildDistributions() {
    def tasks = BuildTasks.create(buildContext)
    tasks.buildDistributions()
    tasks.buildUpdaterJar()
  }

  void layoutCoreArtifacts() {
    new IntelliJCoreArtifactsBuilder(buildContext).layoutIntelliJCore()
  }
}