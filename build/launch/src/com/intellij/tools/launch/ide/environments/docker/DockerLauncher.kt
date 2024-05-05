package com.intellij.tools.launch.ide.environments.docker

import com.intellij.tools.launch.DockerLauncherOptions
import com.intellij.tools.launch.DockerNetworkEntry
import com.intellij.tools.launch.PathsProvider
import com.intellij.tools.launch.docker.cli.*
import com.intellij.tools.launch.os.affixIO
import com.intellij.tools.launch.os.pathNotResolvingSymlinks
import com.intellij.util.SystemProperties
import com.sun.security.auth.module.UnixSystem
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import java.io.File
import java.nio.file.Files
import java.util.logging.Logger

internal class DockerLauncher(private val paths: PathsProvider, private val options: DockerLauncherOptions) {
  companion object {
    private val logger = Logger.getLogger(DockerLauncher::class.java.name)
  }

  private val dockerCli = DockerCli(workDir = paths.tempFolder, options.redirectOutputIntoParentProcess, paths.logFolder)

  fun assertCanRun() = dockerCli.info()


  private val uid = UnixSystem().uid.toString()
  private val gid = UnixSystem().gid.toString()
  private val userName: String = System.getProperty("user.name")!!
  private val userHome: String = getCanonicalUserHome(userName)

  fun runInContainer(cmd: List<String>): Pair<Process, String> {
    val containerIdFile = File.createTempFile("cwm.docker.cid", "")

    val dockerCmd = mutableListOf(
      "docker",
      "run",
      // for network configuration (e.g. default gateway)
      "--cap-add=NET_ADMIN",

      "--cidfile",
      containerIdFile.pathNotResolvingSymlinks(),
    )

    val containerName = "${options.containerName}-${System.nanoTime()}".let {
      dockerCmd.add("--name=$it")
      it
    }

    // **** RW ****
    val writeable = listOf(paths.logFolder,
                           paths.tempFolder,
                           paths.configFolder,
                           paths.systemFolder,
                           paths.communityRootFolder.resolve("build/download") // quiche lib
    )

    // docker can create these under root, so we create them ourselves
    writeable.forEach {
      Files.createDirectories(it.toPath())
      dockerCmd.addWriteable(it)
    }

    // **** RO ****
    dockerCmd.addReadonly(paths.javaHomeFolder)
    dockerCmd.addReadonly(paths.outputRootFolder)

    // Required to ultimate root detection
    dockerCmd.addReadonly(paths.ultimateRootMarker)

    // jars
    dockerCmd.addReadonly(paths.communityBinFolder)
    dockerCmd.addReadonly(paths.communityRootFolder.resolve("lib"))
    dockerCmd.addReadonly(paths.sourcesRootFolder.resolve("lib"))
    dockerCmd.addReadonly(paths.sourcesRootFolder.resolve("plugins"))
    dockerCmd.addReadonly(paths.sourcesRootFolder.resolve("contrib"))

    // on buildserver agents libraries may be cached in ~/.m2.base
    if (TeamCityHelper.isUnderTeamCity) {
      val mavenCache = File(SystemProperties.getUserHome()).resolve(".m2.base")
      if (mavenCache.isDirectory) {
        dockerCmd.addReadonlyIfExists(mavenCache)
      }
    }

    // a lot of jars in classpaths, /plugins, /xml, so we'll just mount the whole root
    dockerCmd.addReadonlyIfExists(paths.communityRootFolder)

    // main jar itself
    dockerCmd.addReadonlyIfExists(paths.launcherFolder)

    // ~/.m2
    dockerCmd.addReadonlyIfExists(paths.mavenRepositoryFolder)

    // quiche
    dockerCmd.addReadonlyIfExists(paths.sourcesRootFolder.resolve(".idea"))

    // user-provided volumes
    paths.dockerVolumesToWritable.forEach { (volume, isWriteable) ->
      dockerCmd.addVolumeIfExists(volume, isWriteable)
    }

    options.exposedPorts.forEach {
      dockerCmd.add("-p")
      dockerCmd.add("127.0.0.1:$it:$it")
    }

    options.environment.forEach {
      dockerCmd.add("--env")
      dockerCmd.add("${it.key}=${it.value}")
    }

    if (options.network != DockerNetworkEntry.AUTO) {
      dockerCmd.addAll(listOf(
        "--network", options.network.name,
        "--ip", options.network.IPAddress
      ))
    }

    dockerCmd.add(options.dockerImageName)

    // docker will still have a route for all subnet packets to go through the host, this only affects anything not in the subnet
    val ipRouteBash = if (options.network != DockerNetworkEntry.AUTO && options.network.defaultGatewayIPv4Address != null) {
      "sudo ip route change default via ${options.network.defaultGatewayIPv4Address}"
    }
    else null

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

    val cmdEscaped = cmd.joinToString(" ").replace("&", "\\&")

    val chownCmd = if (userName != "root") {
      //additional wait so all the folders are created before chown
      "sleep 5; " +
      //chown for all mounted changed directories
      (writeable + paths.dockerVolumesToWritable.filter { it.value }.keys).joinToString("") { "chown -R $uid:$gid $it; " }
    }
    else null

    dockerCmd.addAll(listOf("/bin/bash", "-c"))

    dockerCmd.add(
      bashCommandToAdd + " && " +
      cmdEscaped + "; " +
      chownCmd.orEmpty()
    )

    if (containerIdFile.exists())
      assert(containerIdFile.delete())

    val dockerRunPb = ProcessBuilder(dockerCmd)
    dockerRunPb.affixIO(options.redirectOutputIntoParentProcess, paths.logFolder)

    val dockerRun = dockerRunPb.start()

    val readableCmd = dockerRunPb.command().joinToString("\n")
    logger.info("Docker run cmd=$readableCmd")
    logger.info("Started docker run, waiting for container ID at ${containerIdFile.pathNotResolvingSymlinks()}")

    val containerId = waitForContainerId(dockerRun, containerIdFile)

    waitForContainerToStart(dockerCli, containerName, dockerRun)

    return dockerRun to containerId
  }
}

private fun getCanonicalUserHome(userName: String): String = if (userName == "root") "/root" else "/home/$userName"