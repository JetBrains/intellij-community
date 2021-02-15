package com.intellij.tools.launch

import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.launch.Launcher.affixIO
import com.sun.security.auth.module.UnixSystem
import org.apache.log4j.Logger
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class DockerLauncher(private val paths: PathsProvider, private val options: DockerLauncherOptions) {
  companion object {
    private val logger = Logger.getLogger(DockerLauncher::class.java)
  }

  private val UBUNTU_18_04_WITH_USER_TEMPLATE = "ubuntu-18-04-docker-launcher"

  fun assertCanRun() = dockerInfo()

  fun runInContainer(cmd: List<String>): Process {
    // we try to make everything the same as on the host folder, e.g. UID, paths
    val username = System.getProperty("user.name")
    val uid = UnixSystem().uid.toString()
    val userHome = File(System.getProperty("user.home")).canonicalFile

    if (!userHome.exists()) error("Home directory ${userHome.canonicalPath} of user=$username, uid=$uid does not exist")

    val imageName = "$UBUNTU_18_04_WITH_USER_TEMPLATE-user-$username-uid-$uid"

    val buildArgs = mapOf(
      "USER_NAME" to username,
      "USER_ID" to UnixSystem().uid.toString(),
      "USER_HOME" to userHome.canonicalPath
    )

    dockerBuild(imageName, buildArgs)

    val containerIdFile = File.createTempFile("cwm.docker.cid", "")

    fun File.readonly() = "--volume=${this.canonicalPath}:${this.canonicalPath}:ro"
    fun File.writeable() = "--volume=${this.canonicalPath}:${this.canonicalPath}:rw"

    // docker can create these under root, so we create them ourselves
    Files.createDirectories(paths.logFolder.toPath())
    Files.createDirectories(paths.configFolder.toPath())
    Files.createDirectories(paths.systemFolder.toPath())

    val dockerCmd = mutableListOf(
      "docker",
      "run",

      // for network configuration (e.g. default gateway)
      "--cap-add=NET_ADMIN",

      "--cidfile",
      containerIdFile.canonicalPath,

      // -u=USER_NAME will throw if user does not exist, but -u=UID will not
      "--user=$username",

      // RW
      //paths.tempFolder.writeable(),
      paths.logFolder.writeable(),
      paths.configFolder.writeable(),
      paths.systemFolder.writeable(),
      paths.outputRootFolder.writeable(), // classpath index making a lot of noise in stderr

      // RO
      paths.javaHomeFolder.readonly(),

      // jars
      paths.communityBinFolder.readonly(),
      paths.communityRootFolder.resolve("lib").readonly(),
      paths.ultimateRootFolder.resolve("lib").readonly(),
      paths.ultimateRootFolder.resolve("plugins").readonly(),
      paths.ultimateRootFolder.resolve("contrib").readonly(),

      // a lot of jars in classpaths, /plugins, /xml, so we'll just mount the whole root
      paths.communityRootFolder.readonly(),

      // main jar itself
      paths.launcherFolder.readonly(),

      // ~/.m2
      paths.mavenHomeFolder.readonly(),

      // gold & tmp files for it
      paths.ultimateRootFolder.resolve("platform/intellij-client-tests/data").writeable(),

      // quiche
      paths.ultimateRootFolder.resolve(".idea").readonly(),
      paths.communityRootFolder.resolve("build/download").writeable()
    )

    paths.dockerVolumesToWritable.forEach { (volume, isWritable) ->
      dockerCmd.add(if (isWritable) volume.writeable() else volume.readonly())
    }

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

    if (options.network != DockerNetwork.AUTO && options.network.defaultGatewayIPv4Address != null) {
      dockerCmd.add("/bin/bash")
      dockerCmd.add("-c")

      // docker will still have a route for all subnet packets to go through the host, this only affects anything not in the subnet
      dockerCmd.add("ip route change default ${options.network.defaultGatewayIPv4Address} && ${cmd.joinToString(" ")}}")
    }
    else {
      dockerCmd.addAll(cmd)
    }

    if (containerIdFile.exists())
      assert(containerIdFile.delete())

    val containerIdPath = containerIdFile.canonicalFile.toPath()

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
    assert(containerId.isNotEmpty()) { "Started container ID must not be empty" }
    logger.info("Container ID=$containerId")

    return dockerRun
  }

  private fun dockerInfo() = runCmd(1, TimeUnit.MINUTES, false, paths.tempFolder, "docker", "info")
  private fun dockerKill(containerId: String) = runCmd(1, TimeUnit.MINUTES, false, paths.tempFolder,"docker", "kill", containerId)

  private fun dockerBuild(tag: String, buildArgs: Map<String, String>) {
    val dockerBuildCmd = listOf("docker", "build", "-t", tag).toMutableList()
    buildArgs.forEach {
      dockerBuildCmd.add("--build-arg")
      dockerBuildCmd.add("${it.key}=${it.value}")
    }

    dockerBuildCmd.add(".")

    runCmd(10,
           TimeUnit.MINUTES,
           true,
           paths.communityRootFolder.resolve("build/launch/src/com/intellij/tools/launch"),
           *dockerBuildCmd.toTypedArray())
  }

  private fun runCmd(timeout: Long, unit: TimeUnit, assertSuccess: Boolean, workDir: File, vararg cmd: String): Int {
    if (!SystemInfo.isLinux)
      error("We are heavily relaying on paths being the same everywhere and may use networks, so only Linux can be used as a host system.")

    val processBuilder = ProcessBuilder(*cmd)
    processBuilder.directory(workDir)
    processBuilder.affixIO(options.redirectOutputIntoParentProcess, paths.logFolder)

    val readableCmd = cmd.joinToString(" ", prefix = "'", postfix = "'")
    logger.info(readableCmd)
    val process = processBuilder.start()
    try {
      if (!process.waitFor(timeout, unit))
        error("${cmd[0]} failed to exit under required timeout of $timeout $unit, will destroy it")

      if (assertSuccess)
        assert(process.exitValue() == 0)
    } finally {
      process.destroy()
    }

    return process.exitValue()
  }
}