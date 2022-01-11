package com.intellij.tools.launch

import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.launch.Launcher.affixIO
import com.sun.security.auth.module.UnixSystem
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
    fun MutableList<String>.addWriteable(volume: File) = addVolume(volume, true)

  }

  private val UBUNTU_18_04_WITH_USER_TEMPLATE = "ubuntu-18-04-docker-launcher"

  fun assertCanRun() = dockerInfo()

  fun runInContainer(cmd: List<String>): Process {
    // we try to make everything the same as on the host folder, e.g. UID, paths
    val username = System.getProperty("user.name")
    // docker doesn't like dots and uppercase letters
    val usernameForDockerBuild = username.replace(".", "-").lowercase()
    val uid = UnixSystem().uid.toString()
    val userHomePath = File(System.getProperty("user.home"))



    if (!userHomePath.exists()) error("Home directory ${userHomePath.pathNotResolvingSymlinks()} of user=$username, uid=$uid does not exist")

    val imageName = "$UBUNTU_18_04_WITH_USER_TEMPLATE-user-$usernameForDockerBuild-uid-$uid"

    val buildArgs = mapOf(
      "USER_NAME" to username,
      "USER_ID" to UnixSystem().uid.toString(),
      "USER_HOME" to userHomePath.pathNotResolvingSymlinks()
    )

    dockerBuild(imageName, buildArgs)

    val containerIdFile = File.createTempFile("cwm.docker.cid", "")

    val dockerCmd = mutableListOf(
      "docker",
      "run",

      // for network configuration (e.g. default gateway)
      "--cap-add=NET_ADMIN",

      "--cidfile",
      containerIdFile.pathNotResolvingSymlinks(),

      // -u=USER_NAME will throw if user does not exist, but -u=UID will not
      "--user=$username"
    )

    val containerName = if (options.containerName != null)
      "${options.containerName}-${System.nanoTime()}".let {
        dockerCmd.add("--name=$it")
        it
      }
    else null

    // **** RW ****
    val writeable = listOf(paths.logFolder,
                           paths.configFolder,
                           paths.systemFolder,
                           paths.outputRootFolder, // classpath index making a lot of noise
                           paths.sourcesRootFolder.resolve("platform/cwm-tests/general/data"), // classpath index making a lot of noise in stderr
                           paths.communityRootFolder.resolve("build/download")) // quiche lib

    // docker can create these under root, so we create them ourselves
    writeable.forEach {
      Files.createDirectories(it.toPath())
      dockerCmd.addWriteable(it)
    }

    // **** RO ****
    dockerCmd.addReadonly(paths.javaHomeFolder)

    // jars
    dockerCmd.addReadonly(paths.communityBinFolder)
    dockerCmd.addReadonly(paths.communityRootFolder.resolve("lib"))
    dockerCmd.addReadonly(paths.sourcesRootFolder.resolve("lib"))
    dockerCmd.addReadonly(paths.sourcesRootFolder.resolve("plugins"))
    dockerCmd.addReadonly(paths.sourcesRootFolder.resolve("contrib"))

    // a lot of jars in classpaths, /plugins, /xml, so we'll just mount the whole root
    dockerCmd.addReadonly(paths.communityRootFolder)

    // main jar itself
    dockerCmd.addReadonly(paths.launcherFolder)

    // ~/.m2
    dockerCmd.addReadonly(paths.mavenRepositoryFolder)
    
    // quiche
    dockerCmd.addReadonly(paths.sourcesRootFolder.resolve(".idea"))

    // kotlin
    dockerCmd.addReadonly(paths.sourcesRootFolder.resolve("out").resolve("kotlinc-dist"))

    // user-provided volumes
    paths.dockerVolumesToWritable.forEach { (volume, isWriteable) -> dockerCmd.addVolume(volume, isWriteable) }

    options.exposedPorts.forEach {
      dockerCmd.add("-p")
      dockerCmd.add("127.0.0.1:$it:$it")
    }

    options.environment.forEach {
      dockerCmd.add("--env")
      dockerCmd.add("${it.key}=${it.value}")
    }

    if (options.network != DockerNetwork.AUTO) {
      dockerCmd.addAll(listOf(
        "--network", options.network.name,
        "--ip", options.network.IPv4Address
      ))
    }

    dockerCmd.add(imageName)

    logger.info(dockerCmd.joinToString("\n"))

    // docker will still have a route for all subnet packets to go through the host, this only affects anything not in the subnet
    val ipRouteBash = if (options.network != DockerNetwork.AUTO && options.network.defaultGatewayIPv4Address != null) "sudo ip route change default via ${options.network.defaultGatewayIPv4Address}" else null

    if (options.runBashBeforeJava != null || ipRouteBash != null) {
      dockerCmd.add("/bin/bash")
      dockerCmd.add("-c")

      val cmdHttpLinkSomewhatEscaped = cmd.joinToString(" ").replace("&", "\\&")

      var entrypointBash = ""
      if (ipRouteBash != null)
        entrypointBash += "$ipRouteBash && "
      if (options.runBashBeforeJava != null)
        entrypointBash += "${options.runBashBeforeJava} && "
      dockerCmd.add("$entrypointBash$cmdHttpLinkSomewhatEscaped")
    }
    else {
      dockerCmd.addAll(cmd)
    }

    if (containerIdFile.exists())
      assert(containerIdFile.delete())

    val containerIdPath = containerIdFile.pathNotResolvingSymlinks()

    val dockerRunPb = ProcessBuilder(dockerCmd)
    dockerRunPb.affixIO(options.redirectOutputIntoParentProcess, paths.logFolder)

    val dockerRun = dockerRunPb.start()

    val readableCmd = dockerRunPb.command().joinToString(" ")
    logger.info("Docker run cmd=$readableCmd")
    logger.info("Started docker run, waiting for container ID at ${containerIdPath}")

    // using WatchService is overkill here
    for (i in 1..5) {
      if (!dockerRun.isAlive) error("docker run exited with code ${dockerRun.exitValue()}")

      if (containerIdFile.exists() && containerIdFile.length() > 0) {
          logger.info("Container ID file with non-zero length detected at ${containerIdPath}")
          break;
      }

      val sleepMillis = 100L * 2.0.pow(i).toLong()
      logger.info("No container ID in file ${containerIdPath} yet, sleeping for $sleepMillis")
      Thread.sleep(sleepMillis)
    }

    val containerId = containerIdFile.readText()
    if (containerId.isEmpty()) error { "Started container ID must not be empty" }
    logger.info("Container ID=$containerId")

    if (containerName != null) {
      fun isInDockerPs() = runCmd(1, TimeUnit.MINUTES, true, paths.tempFolder, true, "docker", "ps").count { it.contains(containerName) } > 0

      for (i in 1..5) {
        if (!dockerRun.isAlive) error("docker run exited with code ${dockerRun.exitValue()}")

        if (isInDockerPs()) {
          logger.info("Container with name $containerName detected in docker ps output")
          break;
        }

        val sleepMillis = 100L * 2.0.pow(i).toLong()
        logger.info("No container with name $containerName in docker ps output, sleeping for $sleepMillis")
        Thread.sleep(sleepMillis)
      }
    }

    return dockerRun
  }

  private fun dockerInfo() = runCmd(1, TimeUnit.MINUTES, false, paths.tempFolder, false, "docker", "info")
  private fun dockerKill(containerId: String) = runCmd(1, TimeUnit.MINUTES, false, paths.tempFolder,false, "docker", "kill", containerId)

  private fun dockerBuild(tag: String, buildArgs: Map<String, String>) {
    val dockerBuildCmd = listOf("docker", "build", "-t", tag).toMutableList()
    buildArgs.forEach {
      dockerBuildCmd.add("--build-arg")
      dockerBuildCmd.add("${it.key}=${it.value}")
    }

    dockerBuildCmd.add(".")

    val res = runCmd(10,
           TimeUnit.MINUTES,
           true,
           paths.communityRootFolder.resolve("build/launch/src/com/intellij/tools/launch"),
           true,
           *dockerBuildCmd.toTypedArray())

    logger.info(res.toString())
  }


  private fun runCmd(timeout: Long, unit: TimeUnit, assertSuccess: Boolean, workDir: File, captureOutput: Boolean = false, vararg cmd: String): List<String> {
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
        if (process.exitValue() != 0) error("${cmd[0]} exited with non-zero exit code ${process.exitValue()}. Full commandline: ${cmd.joinToString(" ")}")
    } finally {
      process.destroy()
    }

    return stdoutFile.readLines()
  }
}