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
          module("intellij.platform.util.rt")
          module("intellij.platform.util")
          module("intellij.platform.core")
          module("intellij.platform.core.impl")
          module("intellij.platform.extensions")
          module("intellij.java.psi")
          module("intellij.java.psi.impl")
        }

        jar("annotations.jar") {
          module("intellij.platform.annotations.common")
          module("intellij.platform.annotations.java5")
        }

        jar("intellij-core-analysis.jar") {
          analysisModules.each { module it }
        }

        [
          "ASM", "Guava", "picocontainer", "Trove4j", "cli-parser", "lz4-java", "jayatana", "imgscalr", "batik", "xmlgraphics-commons",
         "JDOM", "OroMatcher", "jna", "Log4J", "StreamEx"
        ].each {
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
