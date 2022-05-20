// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule

@CompileStatic
class MavenArtifactBuilderHelper {
  @SuppressWarnings("GrUnresolvedAccess")
  @CompileStatic(TypeCheckingMode.SKIP)
  static void layoutMavenArtifacts(Map<MavenArtifactData, List<JpsModule>> modulesToPublish, String outputDir, BuildContext context) {
    def publishSourcesFilter = context.productProperties.mavenArtifacts.publishSourcesFilter
    def buildContext = this.buildContext
    Map<MavenArtifactData, String> pomXmlFiles = [:]
    modulesToPublish.each { artifactData, modules ->
      String filePath = "$buildContext.paths.temp/pom-files/${artifactData.coordinates.getDirectoryPath()}/${artifactData.coordinates.getFileName("", "pom")}"
      pomXmlFiles[artifactData] = filePath
      generatePomXmlFile(filePath, artifactData)
    }
    AntBuilder ant = LayoutBuilder.ant
    new LayoutBuilder(buildContext).layout("$buildContext.paths.artifacts/$outputDir") {
      modulesToPublish.each { artifactData, modules ->
        dir(artifactData.coordinates.directoryPath) {
          ant.fileset(file: pomXmlFiles[artifactData])
          List<JpsModule> modulesWithSources = modules.findAll { aModule ->
            !aModule.getSourceRoots(JavaSourceRootType.SOURCE).isEmpty() || !aModule.getSourceRoots(JavaResourceRootType.RESOURCE).isEmpty()
          }

          ant.jar(name: artifactData.coordinates.getFileName("", "jar"), duplicate: "fail",
                  filesetmanifest: "merge", whenmanifestonly: "create") {
            modulesWithSources.forEach { aModule ->
              module(aModule.name)
            }
          }

          List<JpsModule> publishSourcesForModules = modules.findAll { aModule -> publishSourcesFilter.test(aModule, buildContext) }
          if (!publishSourcesForModules.isEmpty() && !modulesWithSources.isEmpty()) {
            zip(artifactData.coordinates.getFileName("sources", "jar")) {
              publishSourcesForModules.forEach { aModule ->
                aModule.getSourceRoots(JavaSourceRootType.SOURCE).each { root ->
                  ant.zipfileset(dir: root.file.absolutePath, prefix: root.properties.packagePrefix.replace('.', '/'))
                }
                aModule.getSourceRoots(JavaResourceRootType.RESOURCE).each { root ->
                  ant.zipfileset(dir: root.file.absolutePath, prefix: root.properties.relativeOutputPath)
                }
              }
            }
          }
        }
      }
    }
  }
}
