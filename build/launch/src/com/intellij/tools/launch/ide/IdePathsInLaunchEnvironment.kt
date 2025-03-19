package com.intellij.tools.launch.ide

import com.intellij.tools.launch.environments.PathInLaunchEnvironment

/**
 * This is the point of view on IDE's system configuration from the local machine.
 */
interface IdePathsInLaunchEnvironment {
  val classPathArgFile: PathInLaunchEnvironment
  val sourcesRootFolder: PathInLaunchEnvironment
  val outputRootFolder: PathInLaunchEnvironment

  val tempFolder: PathInLaunchEnvironment

  val logFolder: PathInLaunchEnvironment
  val configFolder: PathInLaunchEnvironment
  val systemFolder: PathInLaunchEnvironment

  val javaExecutable: PathInLaunchEnvironment
}