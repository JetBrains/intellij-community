// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.intellij.openapi.application.productMigration

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.PreemptiveSafeFileOutputStream
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.isDirectory

// a getter, because the startup calls `migrationStateFile` before the log is set up
private val LOG: Logger
  get() = logger<MigrationState>()

/** The config import skips this file, because it is about the IDE that wrote it. */
internal const val MIGRATION_STATE_FILE_NAME: String = "migration.json"
private const val FORMAT_VERSION = 1

@ApiStatus.Internal
enum class MigrationStatus(val id: String) {
  /** The Unified IDE is installed. */
  INSTALLED("installed"),

  /** The IDE started the Unified IDE and exited. */
  MOVED("moved"),
}

/** The state of the migration. [app] is the absolute path of the Unified IDE. [product] is the product code of the distribution. */
@ApiStatus.Internal
class MigrationState(val status: MigrationStatus, val app: Path, val product: String) {
  fun withStatus(status: MigrationStatus): MigrationState = MigrationState(status, app, product)
}

/** The state file is a plain file in the config dir, because the startup code reads it before the services exist. */
@ApiStatus.Internal
fun migrationStateFile(configDir: Path = PathManager.getConfigDir()): Path = configDir.resolve(MIGRATION_STATE_FILE_NAME)

/**
 * Returns the state in [file], or `null` when the file does not exist.
 * A valid state is not about this IDE, and its Unified IDE exists and has the product code [MigrationState.product].
 * An invalid file is deleted, and the log gets the reason.
 */
@ApiStatus.Internal
fun readMigrationState(file: Path): MigrationState? {
  val state = readUncheckedMigrationState(file) ?: return null
  return validateMigrationState(state, file, readInstalledProductInfo(state.app))
}

/** Returns the state in [file] without the check of the Unified IDE. An invalid file is deleted. */
internal fun readUncheckedMigrationState(file: Path): MigrationState? {
  val text = try {
    Files.readString(file)
  }
  catch (_: NoSuchFileException) {
    return null
  }
  catch (e: IOException) {
    LOG.warn("cannot read ${file}", e)
    return null
  }

  return try {
    parseMigrationState(text)
  }
  catch (e: InvalidMigrationJsonException) {
    discard(file, e.message!!)
  }
}

/**
 * Returns [state] when it passes the check of [readMigrationState], else deletes [file].
 * [info] is the product info of [MigrationState.app].
 */
internal fun validateMigrationState(state: MigrationState, file: Path, info: InstalledProductInfo?): MigrationState? {
  if (state.product == ApplicationInfoImpl.getShadowInstance().build.productCode) {
    return discard(file, "the state is about this IDE: ${state.product}")
  }
  if (!state.app.isDirectory()) {
    return discard(file, "the Unified IDE does not exist: ${state.app}")
  }
  if (info?.productCode != state.product) {
    return discard(file, "${state.app} has the product code ${info?.productCode}, expected ${state.product}")
  }
  return state
}

/** Writes a temporary file next to [file], then renames it. */
@ApiStatus.Internal
@Throws(IOException::class)
fun writeMigrationState(state: MigrationState, file: Path) {
  Files.createDirectories(file.parent)
  PreemptiveSafeFileOutputStream(file).use { it.write(formatMigrationState(state).toByteArray(Charsets.UTF_8)) }
}

/** Deletes the state [file] and the content copy next to it. A failure is only logged. */
@ApiStatus.Internal
fun deleteMigrationState(file: Path) {
  deleteLogged(file)
  deleteLogged(migrationRedirectContentFile(file))
}

/** The data of the `product-info.json` of an installed IDE. [launcher] is an executable file inside the IDE. */
@ApiStatus.Internal
class InstalledProductInfo(val productCode: String?, val launcher: Path?)

/**
 * Returns the `product-info.json` data of the installed IDE [app], or `null` when the file does not exist or is not valid.
 * The startup calls it before the services exist, so it uses the path rule of `IdeProductInfo.loadProductInfo` and not the service.
 * The launcher is the `launcherPath` of the first `launch` record of [os], relative to the folder of the file.
 */
@ApiStatus.Internal
fun readInstalledProductInfo(app: Path, os: OS = OS.CURRENT): InstalledProductInfo? {
  val file = productInfoFile(app)
  val text = try {
    Files.readString(file)
  }
  catch (_: NoSuchFileException) {
    return null
  }
  catch (e: IOException) {
    LOG.warn("cannot read ${file}", e)
    return null
  }

  val osName = launchOsName(os)
  var productCode: String? = null
  var launcherPath: String? = null
  try {
    parseMigrationJson(text) { parser, name ->
      when (name) {
        "productCode" -> productCode = parser.stringOrNull()
        "launch" -> launcherPath = readLauncherPath(parser, osName)
        else -> parser.skipChildren()
      }
    }
  }
  catch (e: InvalidMigrationJsonException) {
    LOG.info("invalid ${file}: ${e.message}")
    return null
  }
  return InstalledProductInfo(productCode, launcherPath?.let { resolveLauncher(app, file, it) })
}

private fun resolveLauncher(app: Path, file: Path, relativePath: String): Path? {
  val launcher = try {
    file.parent.resolve(relativePath).normalize()
  }
  catch (_: InvalidPathException) {
    LOG.info("invalid launcher path in ${file}: ${relativePath}")
    return null
  }
  if (!launcher.startsWith(app.normalize()) || !Files.isRegularFile(launcher) || !Files.isExecutable(launcher)) {
    LOG.info("the launcher is not an executable file in ${app}: ${launcher}")
    return null
  }
  return launcher
}

private fun parseMigrationState(text: String): MigrationState {
  var version: Int? = null
  var statusId: String? = null
  var appPath: String? = null
  var product: String? = null
  parseMigrationJson(text) { parser, name ->
    when (name) {
      "version" -> version = parser.intOrNull()
      "status" -> statusId = parser.stringOrNull()
      "app" -> appPath = parser.stringOrNull()
      "product" -> product = parser.stringOrNull()
      else -> parser.skipChildren()
    }
  }

  if (version != FORMAT_VERSION) invalidMigrationJson("unsupported 'version': ${version}")
  val status = MigrationStatus.entries.find { it.id == statusId } ?: invalidMigrationJson("unknown 'status': ${statusId}")
  val validAppPath = appPath?.takeIf { it.isNotBlank() } ?: invalidMigrationJson("no 'app'")
  val app = try {
    Path.of(validAppPath)
  }
  catch (_: IllegalArgumentException) {
    invalidMigrationJson("invalid 'app': ${validAppPath}")
  }
  if (!app.isAbsolute) invalidMigrationJson("'app' is not absolute: ${validAppPath}")
  val validProduct = product?.takeIf { it.isNotBlank() } ?: invalidMigrationJson("no 'product'")
  return MigrationState(status, app, validProduct)
}

private fun formatMigrationState(state: MigrationState): String = formatMigrationJson {
  writeNumberProperty("version", FORMAT_VERSION)
  writeStringProperty("status", state.status.id)
  writeStringProperty("app", state.app.toString())
  writeStringProperty("product", state.product)
}

/** The `os` value of the `launch` records of [os] in `product-info.json`, or `null` when no distribution has a record of [os]. */
private fun launchOsName(os: OS): String? = when (os) {
  OS.Windows -> "Windows"
  OS.macOS -> "macOS"
  OS.Linux -> "Linux"
  OS.FreeBSD, OS.OHOS, OS.Other -> null
}

/**
 * Reads the `launch` array at the current token of [parser].
 * Returns the `launcherPath` of its first record with the `os` value [osName].
 */
private fun readLauncherPath(parser: JsonParser, osName: String?): String? {
  if (parser.currentToken() != JsonToken.START_ARRAY) invalidMigrationJson("'launch' is not an array")
  var result: String? = null
  while (parser.nextToken() != JsonToken.END_ARRAY) {
    var os: String? = null
    var launcherPath: String? = null
    readMigrationJsonObject(parser) { recordParser, name ->
      when (name) {
        "os" -> os = recordParser.stringOrNull()
        "launcherPath" -> launcherPath = recordParser.stringOrNull()
        else -> recordParser.skipChildren()
      }
    }
    if (result == null && osName != null && os == osName) result = launcherPath
  }
  return result
}

/** The `product-info.json` of the installed IDE [app]. This is the path rule of `IdeProductInfo.loadProductInfo`. */
private fun productInfoFile(app: Path): Path {
  val home = app.resolve("Contents").takeIf { it.isDirectory() } ?: app
  val macFile = home.resolve(ApplicationEx.PRODUCT_INFO_FILE_NAME_MAC)
  return if (Files.exists(macFile)) macFile else home.resolve(ApplicationEx.PRODUCT_INFO_FILE_NAME)
}

private fun discard(file: Path, reason: String): MigrationState? {
  LOG.info("the migration state is ignored and deleted: ${reason}")
  deleteMigrationState(file)
  return null
}

internal fun deleteLogged(file: Path) {
  try {
    Files.deleteIfExists(file)
  }
  catch (e: IOException) {
    LOG.warn("cannot delete ${file}", e)
  }
}
