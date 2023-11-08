package com.intellij.tools.launch

import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.launch.Launcher.affixIO
import com.intellij.util.SystemProperties
import com.sun.security.auth.module.UnixSystem
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.math.pow

class DockerLauncher(private val paths: PathsProvider, private val options: DockerLauncherOptions) {
  companion object {
    private val logger = Logger.getLogger(DockerLauncher::class.java.name)

    // e.g. ~/.m2/ will be /mnt/cache/.m2 on TC
    fun File.pathNotResolvingSymlinks(): String = this.absoluteFile.normalize().path

    fun MutableList<String>.addVolume(volume: File, isWritable: Boolean) {
      fun volumeParameter(volume: String, isWritable: Boolean) = "--volume=$volume:$volume:${if (isWritable) "rw" else "ro"}"

      val canonical = volume.canonicalPath
      this.add(volumeParameter(canonical, isWritable))

      // there's no consistency as to whether symlinks are resolved in user code, so we'll try our best and provide both
      val notResolvingSymlinks = volume.pathNotResolvingSymlinks()
      if (canonical != notResolvingSymlinks)
        this.add(volumeParameter(notResolvingSymlinks, isWritable))
    }

    fun MutableList<String>.addReadonly(volume: File) = addVolume(volume, false)
    fun MutableList<String>.addReadonlyIfExists(volume: File) {
      addVolumeIfExists(volume, false)
    }

    fun MutableList<String>.addVolumeIfExists(volume: File, isWritable: Boolean) {
      if (volume.exists()) addVolume(volume, isWritable)
    }

    fun MutableList<String>.addWriteable(volume: File) = addVolume(volume, true)

  }

  fun assertCanRun() = dockerInfo()


  private val uid = UnixSystem().uid.toString()
  private val gid = UnixSystem().gid.toString()
  private val userName: String = System.getProperty("user.name")!!
  private val userHome: String = "/home/$userName"

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

    val containerIdPath = containerIdFile.pathNotResolvingSymlinks()

    val dockerRunPb = ProcessBuilder(dockerCmd)
    dockerRunPb.affixIO(options.redirectOutputIntoParentProcess, paths.logFolder)

    val dockerRun = dockerRunPb.start()

    val readableCmd = dockerRunPb.command().joinToString("\n")
    logger.info("Docker run cmd=$readableCmd")
    logger.info("Started docker run, waiting for container ID at ${containerIdPath}")

    val startTimestamp = System.currentTimeMillis()
    val timeoutMs = 40_000
    while (System.currentTimeMillis() - startTimestamp < timeoutMs) {
      if (!dockerRun.isAlive) error("docker run exited with code ${dockerRun.exitValue()}")

      if (containerIdFile.exists() && containerIdFile.length() > 0) {
        logger.info("Container ID file with non-zero length detected at ${containerIdPath}")
        break
      }

      Thread.sleep(3_000)
    }

    val containerId = containerIdFile.readText()
    if (containerId.isEmpty()) error("Started container ID must not be empty")
    logger.info("Container ID=$containerId")

    fun isInDockerPs() =
      runCmd(1, TimeUnit.MINUTES, true, paths.tempFolder, true, "docker", "ps")
        .count { it.contains(containerName) } > 0

    for (i in 1..5) {
      if (!dockerRun.isAlive) error("docker run exited with code ${dockerRun.exitValue()}")

      if (isInDockerPs()) {
        logger.info("Container with name $containerName detected in docker ps output")
        break
      }

      val sleepMillis = 100L * 2.0.pow(i).toLong()
      logger.info("No container with name $containerName in docker ps output, sleeping for $sleepMillis")
      Thread.sleep(sleepMillis)
    }

    return dockerRun to containerId
  }

  private fun dockerInfo() = runCmd(1, TimeUnit.MINUTES, false, paths.tempFolder, false, "docker", "info")
  private fun dockerKill(containerId: String) = runCmd(1, TimeUnit.MINUTES, false, paths.tempFolder, false, "docker", "kill", containerId)

  private fun runCmd(timeout: Long,
                     unit: TimeUnit,
                     assertSuccess: Boolean,
                     workDir: File,
                     captureOutput: Boolean = false,
                     vararg cmd: String): List<String> {
    if (!SystemInfo.isLinux)
      error("We are heavily relaying on paths being the same everywhere and may use networks, so only Linux can be used as a host system.")

    val processBuilder = ProcessBuilder(*cmd)
    processBuilder.directory(workDir)

    val stdoutFile = File.createTempFile(cmd[0], "out")
    @Suppress("SSBasedInspection")
    stdoutFile.deleteOnExit()

    if (!captureOutput)
      processBuilder.affixIO(options.redirectOutputIntoParentProcess, paths.logFolder)
    else {
      processBuilder.redirectOutput(stdoutFile)
      processBuilder.redirectError(stdoutFile)
    }

    val readableCmd = cmd.joinToString(" ", prefix = "'", postfix = "'")
    logger.info(readableCmd)
    val process = processBuilder.start()
    try {
      if (!process.waitFor(timeout, unit))
        error("${cmd[0]} failed to exit under required timeout of $timeout $unit, will destroy it")

      if (assertSuccess)
        if (process.exitValue() != 0)
          error("${cmd[0]} exited with non-zero exit code ${process.exitValue()}. Full commandline: ${cmd.joinToString(" ")}")
    }
    finally {
      process.destroy()
    }

    return stdoutFile.readLines()
  }
}