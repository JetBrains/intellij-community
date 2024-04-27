package com.intellij.tools.launch.docker.cli

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.launch.Launcher.affixIO
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
    if (!SystemInfo.isLinux)
      error("We are heavily relaying on paths being the same everywhere and may use networks, so only Linux can be used as a host system.")

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