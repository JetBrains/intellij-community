package com.intellij.tools.launch.docker.cli

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.tools.launch.os.affixIO
import com.intellij.tools.launch.os.pathNotResolvingSymlinks
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * @param workDir the default working directory used to start Docker CLI processes
 */
internal class DockerCli(
  private val workDir: File,
  private val redirectOutputIntoParentProcess: Boolean,
  private val logFolder: File
) {
  fun info(timeout: Duration = DEFAULT_TIMEOUT) {
    runCmd(timeout, assertSuccess = false, captureOutput = false, "docker", "info")
  }

  fun killContainer(containerId: String, timeout: Duration = DEFAULT_TIMEOUT) {
    runCmd(timeout, assertSuccess = false, captureOutput = false, "docker", "kill", containerId)
  }

  /**
   * Returns raw output of `docker ps` command.
   */
  fun listContainers(timeout: Duration = DEFAULT_TIMEOUT): List<String> =
    runCmd(timeout, assertSuccess = true, captureOutput = true, "docker", "ps")

  private fun runCmd(timeout: Duration,
                     assertSuccess: Boolean,
                     captureOutput: Boolean = false,
                     vararg cmd: String): List<String> {
    val processBuilder = ProcessBuilder(*cmd)
    processBuilder.directory(workDir)

    val stdoutFile = File.createTempFile(cmd[0], "out")
    @Suppress("SSBasedInspection")
    stdoutFile.deleteOnExit()

    if (!captureOutput)
      processBuilder.affixIO(redirectOutputIntoParentProcess, logFolder)
    else {
      processBuilder.redirectOutput(stdoutFile)
      processBuilder.redirectError(stdoutFile)
    }

    val readableCmd = cmd.joinToString(" ", prefix = "'", postfix = "'")
    thisLogger().info(readableCmd)
    val process = processBuilder.start()
    try {
      if (!process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS))
        error("${cmd[0]} failed to exit under required timeout of $timeout, will destroy it")

      if (assertSuccess)
        if (process.exitValue() != 0)
          error("${cmd[0]} exited with non-zero exit code ${process.exitValue()}. Full commandline: ${cmd.joinToString(" ")}")
    }
    finally {
      process.destroy()
    }

    return stdoutFile.readLines()
  }

  companion object {
    private val DEFAULT_TIMEOUT: Duration = 1.minutes
  }
}

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