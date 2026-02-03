package com.intellij.tools.launch.docker.cli

import com.intellij.openapi.diagnostic.logger
import com.intellij.tools.launch.docker.cli.DockerCliUtil.logger
import com.intellij.tools.launch.os.pathNotResolvingSymlinks
import java.io.File
import kotlin.math.pow

internal fun waitForContainerId(dockerRun: Process, containerIdFile: File): String {
  val startTimestamp = System.currentTimeMillis()
  val timeoutMs = 40_000
  while (System.currentTimeMillis() - startTimestamp < timeoutMs) {
    if (!dockerRun.isAlive) error("docker run exited with code ${dockerRun.exitValue()}")

    if (containerIdFile.exists() && containerIdFile.length() > 0) {
      logger.info("Container ID file with non-zero length detected at ${containerIdFile.pathNotResolvingSymlinks()}")
      break
    }

    Thread.sleep(3_000)
  }

  val containerId = containerIdFile.readText()
  if (containerId.isEmpty()) error("Started container ID must not be empty")
  logger.info("Container ID=$containerId")
  return containerId
}

internal fun waitForContainerToStart(dockerCli: DockerCli, containerName: String, dockerRun: Process) {
  fun isInDockerPs() =
    dockerCli.listContainers()
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
}

private object DockerCliUtil {
  val logger = logger<DockerCliUtil>()
}