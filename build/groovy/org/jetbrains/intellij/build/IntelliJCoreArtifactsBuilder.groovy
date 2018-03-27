/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import org.jetbrains.intellij.build.impl.LayoutBuilder
/**
 * Builds artifacts which are used in Kotlin Compiler and UpSource
 *
 * @author nik
 */
class IntelliJCoreArtifactsBuilder {
  private static final List<String> ANALYSIS_API_MODULES = [
    "analysis-api",
    "boot",
    "core-api",
    "duplicates-analysis",
    "editor-ui-api",
    "editor-ui-ex",
    "extensions",
    "indexing-api",
    "java-analysis-api",
    "java-indexing-api",
    "java-psi-api",
    "java-structure-view",
    "jps-model-api",
    "jps-model-serialization",
    "projectModel-api",
    "util",
    "util-rt",
    "xml-analysis-api",
    "xml-psi-api",
    "xml-structure-view-api",
  ]
  private static final List<String> ANALYSIS_IMPL_MODULES = [
    "analysis-impl",
    "core-impl",
    "indexing-impl",
    "java-analysis-impl",
    "java-indexing-impl",
    "java-psi-impl",
    "projectModel-impl",
    "structure-view-impl",
    "xml-analysis-impl",
    "xml-psi-impl",
    "xml-structure-view-impl",
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
      String home = buildContext.paths.communityHome
      List<String> analysisModules = ANALYSIS_API_MODULES + ANALYSIS_IMPL_MODULES
      new LayoutBuilder(buildContext, false).layout(coreArtifactDir) {
        jar("intellij-core.jar") {
          module("util-rt")
          module("util")
          module("core-api")
          module("core-impl")
          module("extensions")
          module("java-psi-api")
          module("java-psi-impl")
        }

        jar("annotations.jar") {
          module("annotations-common")
          module("annotations")
        }

        jar("intellij-core-analysis.jar") {
          analysisModules.each { module it }
        }

        ["ASM", "Guava", "picocontainer", "Trove4j", "cli-parser", "Snappy-Java", "jayatana", "imgscalr", "batik", "xmlgraphics-commons"].each {
          projectLibrary(it)
        }
      }
      buildContext.notifyArtifactBuilt(coreArtifactDir)

      def intellijCoreZip = "${buildContext.paths.artifacts}/intellij-core-${buildContext.buildNumber}.zip"
      ant.zip(destfile: intellijCoreZip) {
        fileset(dir: coreArtifactDir)
      }
      buildContext.notifyArtifactBuilt(intellijCoreZip)
    }
  }
}
