// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.productMigration

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.SystemProperties
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private val LOG = logger<MigrationLaunchContract>()

private const val SETSID = "setsid"

// the desktop gives them to the old IDE for its own start, and a child must not use them a second time
private val LINUX_DESKTOP_VARIABLES = listOf("DESKTOP_STARTUP_ID", "GIO_LAUNCHED_DESKTOP_FILE", "GIO_LAUNCHED_DESKTOP_FILE_PID", "XDG_ACTIVATION_TOKEN")

/**
 * The launch contract between the old IDE and the Unified IDE.
 *
 * The old IDE starts the Unified IDE with these program arguments:
 * ```
 * -Dide.migration.version=1
 * -Dide.migration.reason=migrate
 * -Dide.migration.source.product=GO
 * <project or file path> ...
 * ```
 *
 * - [VERSION_PROPERTY]: the version of the contract. The only version is [VERSION].
 * - [REASON_PROPERTY]: the [MigrationLaunchReason], `migrate` or `redirect`.
 * - [SOURCE_PRODUCT_PROPERTY]: the product code of the old IDE, for example `GO`.
 * - The positional arguments: for `migrate`, the open projects of the old IDE. For `redirect`, the forwarded arguments of the old IDE.
 *
 * `Main.preprocessArgs` makes a system property from a `-Dkey=value` argument.
 * The `-D` arguments come first, because some builds read only the leading ones.
 *
 * The values exist only in the first session of the Unified IDE, because a restart does not keep them.
 * The Unified IDE must save what it needs before its first restart.
 *
 * The contract has no settings location. The Unified IDE finds the settings and the plugins of the source IDE itself.
 *
 * The compatibility rule: version 1 keys never change their meaning. A new optional key keeps version 1. A changed meaning needs version 2.
 * The reader ignores all values when the version is missing or unknown. It ignores unknown keys, so a later version can add keys.
 *
 * The old IDE writes the arguments with [migrationLaunchArguments]. The Unified IDE reads them with [readMigrationLaunchParameters].
 */
@ApiStatus.Internal
object MigrationLaunchContract {
  const val VERSION: Int = 1
  const val VERSION_PROPERTY: String = "ide.migration.version"
  const val REASON_PROPERTY: String = "ide.migration.reason"
  const val SOURCE_PRODUCT_PROPERTY: String = "ide.migration.source.product"
}

/** Why the old IDE starts the Unified IDE. [value] is the value of [MigrationLaunchContract.REASON_PROPERTY]. */
@ApiStatus.Internal
enum class MigrationLaunchReason(val value: String) {
  /** "Restart to Update". The Unified IDE imports the data of the source IDE and shows the welcome dialog for it. */
  MIGRATE("migrate"),

  /** A redirected start of the old IDE. The Unified IDE starts as usual. */
  REDIRECT("redirect"),
}

/** The values that the Unified IDE gets from the old IDE. */
@ApiStatus.Internal
data class MigrationLaunchParameters(val reason: MigrationLaunchReason, val sourceProduct: String)

/**
 * Reads the [MigrationLaunchContract] from [property], or returns `null`.
 * The result is `null` when the version is not [MigrationLaunchContract.VERSION], when the reason is missing or unknown,
 * or when the source product is missing or blank.
 * [property] reads a system property, so a test can give other values.
 */
@ApiStatus.Internal
fun readMigrationLaunchParameters(property: (String) -> String? = System::getProperty): MigrationLaunchParameters? {
  if (property(MigrationLaunchContract.VERSION_PROPERTY)?.toIntOrNull() != MigrationLaunchContract.VERSION) return null
  val reasonValue = property(MigrationLaunchContract.REASON_PROPERTY)
  val reason = MigrationLaunchReason.entries.firstOrNull { it.value == reasonValue } ?: return null
  val sourceProduct = property(MigrationLaunchContract.SOURCE_PRODUCT_PROPERTY)?.takeIf { it.isNotBlank() } ?: return null
  return MigrationLaunchParameters(reason, sourceProduct)
}

/**
 * The arguments of the [MigrationLaunchContract] that start the installed application.
 * [sourceProduct] is the product code of this IDE. [arguments] are the positional arguments.
 */
@ApiStatus.Internal
fun migrationLaunchArguments(reason: MigrationLaunchReason, sourceProduct: String, arguments: List<String>): List<String> =
  listOf(
    "-D${MigrationLaunchContract.VERSION_PROPERTY}=${MigrationLaunchContract.VERSION}",
    "-D${MigrationLaunchContract.REASON_PROPERTY}=${reason.value}",
    "-D${MigrationLaunchContract.SOURCE_PRODUCT_PROPERTY}=${sourceProduct}",
  ) + arguments

/** The macOS command that starts a new instance of [app] with [arguments]. Each value is a separate argument. */
@ApiStatus.Internal
fun macOpenCommand(app: Path, arguments: List<String>): List<String> =
  listOf("/usr/bin/open", "-n", "-a", app.toString(), "--args") + arguments

/** The command that runs [launcher], the launcher of the installed application, with [arguments]. */
@ApiStatus.Internal
fun migrationLauncherCommand(launcher: Path, arguments: List<String>): List<String> = listOf(launcher.toString()) + arguments

/**
 * The Linux command that runs [command] in a new session, so the process does not end with the terminal of this IDE.
 * [setsid] is the `setsid` program. It makes the session and then becomes the first program of [command], with the same process ID.
 * With no [setsid], the result is [command], and the process stays in the session of this IDE.
 */
@ApiStatus.Internal
fun linuxDetachedCommand(command: List<String>, setsid: Path?): List<String> =
  if (setsid == null) command else listOf(setsid.toString()) + command

/**
 * Starts [command] and logs the start. A failure is logged and gives `null`.
 * [command] is a [migrationLauncherCommand]. The process is the Unified IDE, and it continues after the exit of this process.
 * So it gets no pipe for its output, and its working directory is the user home and not a folder of this IDE.
 * On Linux, it starts as a [linuxDetachedCommand], without the desktop variables of this IDE.
 * With `setsid`, a launcher that cannot run gives a process that ends, and not `null`.
 */
@ApiStatus.Internal
fun startMigrationLauncher(command: List<String>, os: OS = OS.CURRENT): Process? {
  val fullCommand = if (os == OS.Linux) linuxDetachedCommand(command, findSetsid()) else command
  LOG.info("starting the Unified IDE: ${fullCommand}")
  try {
    val builder = ProcessBuilder(fullCommand)
      .directory(Path.of(SystemProperties.getUserHome()).toFile())
      .redirectOutput(ProcessBuilder.Redirect.DISCARD)
      .redirectError(ProcessBuilder.Redirect.DISCARD)
    if (os == OS.Linux) removeLinuxDesktopVariables(builder)
    return builder.start()
  }
  catch (e: IOException) {
    LOG.warn("cannot start the Unified IDE", e)
    return null
  }
}

/**
 * Starts [command] and logs it. With [inheritIO], the process gets the standard streams of this process.
 * It stays in the session of this process, so the terminal of a start that waits controls it.
 * On Linux, it does not get the desktop variables of this IDE.
 * A failure is logged and gives `null`.
 */
@ApiStatus.Internal
fun startMigrationProcess(command: List<String>, inheritIO: Boolean = false, os: OS = OS.CURRENT): Process? {
  LOG.info("starting the Unified IDE: ${command}")
  try {
    val builder = ProcessBuilder(command)
    if (inheritIO) builder.inheritIO() else builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
    if (os == OS.Linux) removeLinuxDesktopVariables(builder)
    return builder.start()
  }
  catch (e: IOException) {
    LOG.warn("cannot start the Unified IDE", e)
    return null
  }
}

/** Waits up to [timeoutSeconds] for the `open` [process] and logs a failure. Returns `true` when `open` ends with the exit code 0. */
@ApiStatus.Internal
fun waitForMigrationOpen(process: Process, timeoutSeconds: Long): Boolean {
  if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
    LOG.warn("the start of the Unified IDE does not end in ${timeoutSeconds} s")
    return false
  }
  val exitCode = process.exitValue()
  if (exitCode != 0) {
    val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
    LOG.warn("cannot start the Unified IDE: exit code ${exitCode}, ${stderr}")
    return false
  }
  return true
}

/** Looks only in the absolute folders of `PATH`. This module does not see `PathEnvironmentVariableUtil`. */
private fun findSetsid(): Path? {
  val setsid = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).firstNotNullOfOrNull { dir ->
    try {
      Path.of(dir, SETSID).takeIf { it.isAbsolute && Files.isRegularFile(it) && Files.isExecutable(it) }
    }
    catch (_: InvalidPathException) {
      null
    }
  }
  if (setsid == null) LOG.info("no ${SETSID} in PATH, the Unified IDE starts in the session of this IDE")
  return setsid
}

private fun removeLinuxDesktopVariables(builder: ProcessBuilder) {
  builder.environment().keys.removeAll(LINUX_DESKTOP_VARIABLES)
}
