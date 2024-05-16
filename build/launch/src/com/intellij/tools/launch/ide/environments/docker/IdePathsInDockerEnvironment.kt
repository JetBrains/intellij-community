package com.intellij.tools.launch.ide.environments.docker

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.launch.PathsProvider
import com.intellij.tools.launch.environments.PathInLaunchEnvironment
import com.intellij.tools.launch.environments.resolve
import com.intellij.tools.launch.ide.IdePathsInLaunchEnvironment
import java.io.File
import java.nio.file.Paths

internal fun createIdePathsInDockerEnvironment(
  localPaths: PathsProvider,
  dockerContainerOptions: DockerContainerOptions,
  classPathArgFile: File
) =
  if (dockerContainerOptions.legacy) {
    LegacyIdePathsInDockerEnvironment(localPaths, classPathArgFile)
  }
  else {
    IdePathsInDockerEnvironment(localPaths, dockerContainerOptions, classPathArgFile, dockerContainerOptions.javaExecutable)
  }

private class IdePathsInDockerEnvironment(
  private val localPaths: PathsProvider,
  private val dockerContainerOptions: DockerContainerOptions,
  private val localClassPathArgFile: File,
  private val javaExecutableInContainer: PathInLaunchEnvironment
) : IdePathsInLaunchEnvironment {
  private val homePath = Paths.get(PathManager.getHomePath())

  override val classPathArgFile: PathInLaunchEnvironment
    // we know that the file is going to be created inside `localPaths.logFolder` directory
    get() = localClassPathArgFile.toPathWithinHomeInContainer()
  override val sourcesRootFolder: PathInLaunchEnvironment
    get() = localPaths.sourcesRootFolder.toPathWithinHomeInContainer()
  override val outputRootFolder: PathInLaunchEnvironment
    get() = localPaths.outputRootFolder.toPathWithinHomeInContainer()
  override val tempFolder: PathInLaunchEnvironment
    get() = localPaths.tempFolder.toPathWithinHomeInContainer()
  override val logFolder: PathInLaunchEnvironment
    get() = localPaths.logFolder.toPathWithinHomeInContainer()
  override val configFolder: PathInLaunchEnvironment
    get() = localPaths.configFolder.toPathWithinHomeInContainer()
  override val systemFolder: PathInLaunchEnvironment
    get() = localPaths.systemFolder.toPathWithinHomeInContainer()
  override val javaExecutable: PathInLaunchEnvironment
    get() = javaExecutableInContainer

  private fun File.toPathWithinHomeInContainer(): PathInLaunchEnvironment =
    Paths.get(canonicalPath).resolve(
      baseLocalPath = homePath,
      baseEnvPath = dockerContainerOptions.ultimateRepositoryPathInContainer,
      envFileSeparator = UNIX_FILE_SEPARATOR
    )
}

private class LegacyIdePathsInDockerEnvironment(
  private val localPaths: PathsProvider,
  private val localClassPathArgFile: File,
) : IdePathsInLaunchEnvironment {
  init {
    assert(SystemInfo.isLinux) { "Only supported in Linux" }
  }

  override val classPathArgFile: PathInLaunchEnvironment
    get() = localClassPathArgFile.canonicalPath
  override val sourcesRootFolder: PathInLaunchEnvironment
    get() = localPaths.sourcesRootFolder.canonicalPath
  override val outputRootFolder: PathInLaunchEnvironment
    get() = localPaths.outputRootFolder.canonicalPath
  override val tempFolder: PathInLaunchEnvironment
    get() = localPaths.tempFolder.canonicalPath
  override val logFolder: PathInLaunchEnvironment
    get() = localPaths.logFolder.canonicalPath
  override val configFolder: PathInLaunchEnvironment
    get() = localPaths.configFolder.canonicalPath
  override val systemFolder: PathInLaunchEnvironment
    get() = localPaths.systemFolder.canonicalPath
  override val javaExecutable: PathInLaunchEnvironment
    get() = localPaths.javaExecutable.canonicalPath
}