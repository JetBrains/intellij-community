package com.intellij.tools.launch.ide.environments.docker

import com.intellij.tools.launch.DockerLauncherOptions
import com.intellij.tools.launch.PathsProvider
import com.intellij.tools.launch.environments.AbstractCommandLauncher
import com.intellij.tools.launch.environments.LaunchCommand
import com.intellij.tools.launch.environments.LaunchEnvironment
import com.intellij.tools.launch.ide.ClassPathBuilder
import com.intellij.tools.launch.ide.ClasspathCollector
import com.intellij.tools.launch.ide.IdeCommandLauncherFactory
import com.intellij.tools.launch.ide.IdePathsInLaunchEnvironment

interface LocalDockerRunResult {
  val process: Process
  val containerId: String
}

private data class LocalDockerRunResultImpl(
  override val process: Process,
  override val containerId: String,
) : LocalDockerRunResult

fun dockerRunCliCommandLauncherFactory(dockerContainerOptions: DockerContainerOptions): DockerRunCliCommandLauncherFactory {
  val dockerLauncherOptions = DockerLauncherOptionsImpl(
    dockerImageName = dockerContainerOptions.image,
    containerName = dockerContainerOptions.containerName
  )
  return DockerRunCliCommandLauncherFactory(dockerContainerOptions, dockerLauncherOptions)
}

fun legacyDockerRunCliCommandLauncherFactory(dockerLauncherOptions: DockerLauncherOptions,
                                             paths: PathsProvider): DockerRunCliCommandLauncherFactory {
  val dockerContainerOptions = DockerContainerOptions(
    image = dockerLauncherOptions.dockerImageName,
    containerName = dockerLauncherOptions.containerName,
    javaExecutable = paths.javaExecutable.canonicalPath,
    ultimateRepositoryPathInContainer = paths.sourcesRootFolder.canonicalPath,
    legacy = true
  )
  return DockerRunCliCommandLauncherFactory(dockerContainerOptions, dockerLauncherOptions)
}

class DockerRunCliCommandLauncherFactory(
  private val dockerContainerOptions: DockerContainerOptions,
  private val dockerLauncherOptions: DockerLauncherOptions
) : IdeCommandLauncherFactory<LocalDockerRunResult> {
  override fun create(localPaths: PathsProvider,
                      classpathCollector: ClasspathCollector): Pair<AbstractCommandLauncher<LocalDockerRunResult>, IdePathsInLaunchEnvironment> {
    val dockerContainerEnvironment = DockerContainerEnvironment.createDefaultDockerContainerEnvironment(dockerContainerOptions, localPaths)
    // collect classpath with the paths within the Docker container (the container does not exist at this point, it is going to be created later)
    val classpathInDocker = classpathCollector.collect(dockerContainerEnvironment)
    // create the classpath file locally, later it will be mounted inside the Docker container
    // at this point we know that the file is going to be created inside `localPaths.logFolder` directory
    val classPathArgFile = ClassPathBuilder.createClassPathArgFile(localPaths, classpathInDocker, UNIX_PATH_SEPARATOR.toString())
    assert(classPathArgFile.startsWith(localPaths.logFolder)) {
      "IDE's classpath arg file '$classPathArgFile' is expected to be created within the log folder '${localPaths.logFolder}'"
    }
    val paths = createIdePathsInDockerEnvironment(localPaths, dockerContainerOptions, classPathArgFile)
    return DockerRunCliCommandLauncher(localPaths, dockerContainerEnvironment, dockerContainerOptions, dockerLauncherOptions) to paths
  }
}

private class DockerRunCliCommandLauncher(
  val localPaths: PathsProvider,
  val dockerContainerEnvironment: DockerContainerEnvironment,
  val dockerContainerOptions: DockerContainerOptions,
  val dockerLauncherOptions: DockerLauncherOptions,
) : AbstractCommandLauncher<LocalDockerRunResult> {
  override fun launch(buildCommand: LaunchEnvironment.() -> LaunchCommand): LocalDockerRunResult {
    val launchCommand = dockerContainerEnvironment.buildCommand()
    val dockerLauncher = DockerLauncher(localPaths, dockerLauncherOptions)
    val (process, containerId) = dockerLauncher.runInContainer(dockerContainerEnvironment, launchCommand, dockerContainerOptions)
    return LocalDockerRunResultImpl(process, containerId)
  }
}