// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Paths

@ApiStatus.Internal
object BuildDependenciesManualRunOnly {
  @JvmStatic
  val communityRootFromWorkingDirectory: BuildDependenciesCommunityRoot
    get() {
      // This method assumes the current working directory is inside intellij-based product checkout root
      val workingDirectory = Paths.get(System.getProperty("user.dir"))
      var current = workingDirectory
      while (current.parent != null) {
        for (pathCandidate in mutableListOf(".", "community", "ultimate/community")) {
          val probeFile = current.resolve(pathCandidate).resolve("intellij.idea.community.main.iml").normalize()
          if (Files.exists(probeFile)) {
            return BuildDependenciesCommunityRoot(probeFile.parent)
          }
        }
        current = current.parent
      }
      throw IllegalStateException("IDEA Community root was not found from current working directory $workingDirectory")
    }
}
