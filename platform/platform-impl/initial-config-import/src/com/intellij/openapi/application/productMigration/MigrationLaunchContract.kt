// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.productMigration

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private val LOG = logger<MigrationLaunchContract>()

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

/** The command that runs [launcher], the launcher of the installed application, with [arguments]. Each value is a separate argument. */
@ApiStatus.Internal
fun migrationLauncherCommand(launcher: Path, arguments: List<String>): List<String> = listOf(launcher.toString()) + arguments

/**
 * Starts [launcher] directly with [arguments] and logs the start. A failure is logged and gives `null`.
 * The process is the Unified IDE, and it continues after the exit of this process.
 * So it gets no pipe for its output, and its working directory is the user home and not a folder of this IDE.
 */
@ApiStatus.Internal
fun startMigrationLauncher(launcher: Path, arguments: List<String>): Process? {
  val command = migrationLauncherCommand(launcher, arguments)
  LOG.info("starting the Unified IDE: ${command}")
  try {
    return ProcessBuilder(command)
      .directory(Path.of(SystemProperties.getUserHome()).toFile())
      .redirectOutput(ProcessBuilder.Redirect.DISCARD)
      .redirectError(ProcessBuilder.Redirect.DISCARD)
      .start()
  }
  catch (e: IOException) {
    LOG.warn("cannot start the Unified IDE", e)
    return null
  }
}

/**
 * Starts [command] and logs it. With [inheritIO], the process gets the standard streams of this process.
 * A failure is logged and gives `null`.
 */
@ApiStatus.Internal
fun startMigrationProcess(command: List<String>, inheritIO: Boolean = false): Process? {
  LOG.info("starting the Unified IDE: ${command}")
  try {
    val builder = ProcessBuilder(command)
    if (inheritIO) builder.inheritIO() else builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
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
