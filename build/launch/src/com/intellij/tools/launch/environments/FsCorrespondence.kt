package com.intellij.tools.launch.environments

import java.nio.file.Path

interface FsCorrespondence {
  fun tryResolve(localPath: Path): PathInLaunchEnvironment?
}

fun limitedFsCorrespondence(mappings: Map<Path, PathInLaunchEnvironment>, envFileSeparator: Char): FsCorrespondence {
  return object : FsCorrespondence {
    override fun tryResolve(localPath: Path): PathInLaunchEnvironment? =
      mappings.firstNotNullOfOrNull { (hostPath, containerPath) ->
        if (localPath.startsWith(hostPath)) {
          localPath.resolve(baseLocalPath = hostPath, baseEnvPath = containerPath, envFileSeparator = envFileSeparator)
        }
        else null
      }
  }
}