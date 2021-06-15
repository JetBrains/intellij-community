// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.dependencies

import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Use for debugging only or running code from a custom main method.
 * Any production code already has the community home path
 */
@CompileStatic
class BuildDependenciesManualRunOnly {
  static Path getCommunityRootFromWorkingDirectory() {
    // This method assumes the current working directory is inside intellij-based product checkout root
    Path workingDirectory = Paths.get(System.getProperty("user.dir"))

    Path current = workingDirectory
    while (current.parent != null) {
      for (def pathCandidate : [".", "community", "ultimate/community"]) {
        def probeFile = current.resolve(pathCandidate).resolve("intellij.idea.community.main.iml")
        if (Files.exists(probeFile)) {
          return probeFile.parent
        }
      }

      current = current.parent
    }

    throw new IllegalStateException("IDEA Community root was not found from current working directory $workingDirectory")
  }

  static Properties getDependenciesPropertiesFromWorkingDirectory() {
    Path communityRoot = getCommunityRootFromWorkingDirectory()
    Path propertiesFile = communityRoot.resolve("build").resolve("dependencies").resolve("gradle.properties")

    println("Loading properties from $propertiesFile")
    Properties properties = new Properties()
    Files.newBufferedReader(propertiesFile).withCloseable { properties.load(it) }
    return properties
  }
}
