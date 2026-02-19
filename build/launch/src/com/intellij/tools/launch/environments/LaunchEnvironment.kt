package com.intellij.tools.launch.environments

interface LaunchEnvironment {
  fun uid(): String
  fun gid(): String
  fun userName(): String
  fun userHome(): PathInLaunchEnvironment

  fun fsCorrespondence(): FsCorrespondence

  fun resolvePath(base: PathInLaunchEnvironment, relative: PathInLaunchEnvironment): PathInLaunchEnvironment
}