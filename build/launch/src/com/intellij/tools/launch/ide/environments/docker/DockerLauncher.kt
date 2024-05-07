package com.intellij.tools.launch.ide.environments.docker

import com.intellij.tools.launch.DockerLauncherOptions
import com.intellij.tools.launch.DockerNetworkEntry
import com.intellij.tools.launch.PathsProvider
import com.intellij.tools.launch.docker.BindMount
import com.intellij.tools.launch.docker.cli.DockerCli
import com.intellij.tools.launch.docker.cli.dockerRunCliCommand
import com.intellij.tools.launch.docker.cli.dsl.addVolumeIfExists
import com.intellij.tools.launch.docker.cli.waitForContainerId
import com.intellij.tools.launch.docker.cli.waitForContainerToStart
import com.intellij.tools.launch.environments.LaunchCommand
import com.intellij.tools.launch.environments.PathInLaunchEnvironment
import com.intellij.tools.launch.os.affixIO
import com.intellij.tools.launch.os.pathNotResolvingSymlinks
import org.jetbrains.annotations.ApiStatus.Obsolete
import java.io.File
import java.util.logging.Logger

data class DockerContainerOptions(
  val image: String,
  val containerName: String,
  val javaExecutable: PathInLaunchEnvironment,
  /**
   * The container path of the ultimate repo bind mount.
   */
  val ultimateRepositoryPathInContainer: PathInLaunchEnvironment,
  val bindMounts: List<BindMount> = emptyList(),
  /**
   * `true` value of this flag is incompatible with Docker for Windows.
   */
  @Obsolete
  val legacy: Boolean = false,
)

internal class DockerLauncher(private val paths: PathsProvider, private val options: DockerLauncherOptions) {
  companion object {
    private val logger = Logger.getLogger(DockerLauncher::class.java.name)
  }

  private val dockerCli = DockerCli(workDir = paths.tempFolder, options.redirectOutputIntoParentProcess, paths.logFolder)

  fun assertCanRun() = dockerCli.info()

  /**
   * We assume that bind mounts are configured
   */
  fun runInContainer(
    dockerContainerEnvironment: DockerContainerEnvironment,
    launchCommand: LaunchCommand,
    dockerContainerOptions: DockerContainerOptions
  ): Pair<Process, String> {
    val containerIdFile = File.createTempFile("cwm.docker.cid", "")
    val containerName = "${options.containerName}-${System.nanoTime()}"

    val dockerRunCommand = dockerRunCliCommand {
      // for network configuration (e.g. default gateway)
      option("--cap-add=NET_ADMIN")
      options("--cidfile", containerIdFile.pathNotResolvingSymlinks())

      option("--name=$containerName")

      dockerContainerEnvironment.bindMounts.forEach { (hostPath, containerPath) ->
        assert(hostPath.isAbsolute) { "Host path $hostPath bind mount to $containerPath must be absolute" }
        bindMount(hostPath.toString(), containerPath, readOnly = false)
      }

      // user-provided volumes
      paths.dockerVolumesToWritable.forEach { (volume, isWriteable) ->
        addVolumeIfExists(volume, isWriteable)
      }

      options.exposedPorts.forEach {
        option("-p")
        option("127.0.0.1:$it:$it")
      }

      // TODO shouldn't here be left only one?
      (options.environment + launchCommand.environment).forEach {
        option("--env")
        option("${it.key}=${it.value}")
      }

      if (options.network != DockerNetworkEntry.AUTO) {
        option(listOf(
          "--network", options.network.name,
          "--ip", options.network.IPAddress
        ))
      }

      image(options.dockerImageName)

      // docker will still have a route for all subnet packets to go through the host, this only affects anything not in the subnet
      val ipRouteBash = if (options.network != DockerNetworkEntry.AUTO && options.network.defaultGatewayIPv4Address != null) {
        "sudo ip route change default via ${options.network.defaultGatewayIPv4Address}"
      }
      else null

      val uid = dockerContainerEnvironment.uid()
      val gid = dockerContainerEnvironment.gid()
      val userName = dockerContainerEnvironment.userName()
      val userHome = dockerContainerEnvironment.userHome()

      val commands = run {
        val cmdEscaped = launchCommand.commandLine.joinToString(" ").replace("&", "\\&")

        if (dockerContainerOptions.legacy) {
          val userAddCommand = if (userName != "root") {
            // this step is needed to make the user starting autotests owner of the logs files.
            // by default the logs are mounted with root as owner.
            // we need to add current user to docker and then chown logs to it, to be able to remove them
            "groupadd -g $gid $userName && " +
            "useradd -s /bin/bash -d $userHome -u $uid -g $gid -m $userName"
          }
          else null

          val bashCommandToAdd =
            "echo 'started bash' && " +
            options.runBashBackgroundBeforeJava?.joinToString("") { " $it & " }.orEmpty() +
            ipRouteBash?.let { " $it && " }.orEmpty() +
            userAddCommand?.let { " $it && " }.orEmpty() +
            options.runBashBeforeJava?.joinToString("") { " $it && " }.orEmpty() +
            "echo 'finished bash'"

          val chownCmd = if (userName != "root") {
            //additional wait so all the folders are created before chown
            "sleep 5; " +
            //chown for all mounted changed directories
            (
              dockerContainerEnvironment.bindMounts.filter { !it.readonly }.map { it.containerPath } +
              paths.dockerVolumesToWritable.filter { it.value }.keys
            ).joinToString("") { "chown -R $uid:$gid $it; " }
          }
          else null

          "$bashCommandToAdd && $cmdEscaped; ${chownCmd.orEmpty()}"
        }
        else {
          cmdEscaped
        }
      }

      cmd("/bin/bash", "-c", commands)
    }

    if (containerIdFile.exists())
      assert(containerIdFile.delete())

    val result = dockerRunCommand.launch()

    val dockerRunPb = result.createProcessBuilder()
    if (dockerContainerOptions.legacy) {
      dockerRunPb.affixIO(options.redirectOutputIntoParentProcess, paths.logFolder)
    }

    val dockerRun = dockerRunPb.start()

    val readableCmd = dockerRunPb.command().joinToString("\n")
    logger.info("Docker run cmd=$readableCmd")
    logger.info("Started docker run, waiting for container ID at ${containerIdFile.pathNotResolvingSymlinks()}")

    val containerId = waitForContainerId(dockerRun, containerIdFile)

    waitForContainerToStart(dockerCli, containerName, dockerRun)

    return dockerRun to containerId
  }
}