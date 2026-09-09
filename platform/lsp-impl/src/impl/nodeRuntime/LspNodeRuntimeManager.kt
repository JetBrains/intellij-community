// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This file works on the local machine only, so it uses `java.nio` directly instead of the Eel file
// API. See the class documentation of `LspNodeRuntimeManager` for the reason.
// `File.pathSeparator` is the separator of the `PATH` variable, not a file path, and it has no
// `java.nio` equal. The platform's own `PathEnvironmentVariableUtil` suppresses `IO_FILE_USAGE` for
// the same reason.
@file:Suppress("UseOptimizedEelFunctions", "IO_FILE_USAGE")

package com.intellij.platform.lsp.impl.nodeRuntime

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.util.EnvironmentUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.Decompressor
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.sha256Hex
import com.intellij.util.system.CpuArch
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import com.intellij.util.text.VersionComparatorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.exists
import kotlin.io.path.isExecutable

private val logger = logger<LspNodeRuntimeManager>()

/**
 * The `PATH` a process gets: [binDir] first, then the directories that hold the operating system
 * shell.
 *
 * The local `PATH` stays out, so only the managed runtime counts and a `node` the user installed can
 * never win. [binDir] comes first for the same reason.
 *
 * The shell directories must stay in. `npm exec` runs the resolved binary through a shell, so npm
 * fails with `spawn sh ENOENT` when it cannot find `sh`. These directories hold the shell itself and
 * the other standard commands, and they hold no version manager, so they add no second `node`.
 */
@VisibleForTesting
internal fun managedPathValue(binDir: Path): String =
  (listOf(binDir.toString()) + shellDirectories()).joinToString(File.pathSeparator)

/** The directories that hold the operating system shell. */
internal fun shellDirectories(): List<String> =
  if (LspNodeHost.current()?.isWindows == true) {
    val systemRoot = EnvironmentUtil.getValue("SystemRoot") ?: "C:\\Windows"
    listOf("$systemRoot\\System32", systemRoot)
  }
  else {
    listOf("/usr/bin", "/bin")
  }

/**
 * Downloads and manages one pinned Node.js runtime for LSP servers that ship as an npm package.
 *
 * The runtime is a copy of the Node.js distribution from `nodejs.org`, extracted into the IDE system
 * directory ([PathManager.getSystemDir], see [runtimesDir]). That directory holds data the IDE can
 * build again, so a user who deletes it pays one more download and nothing else. A server descriptor
 * gets `node`, `npm` and `npx` from the runtime, so the user does not have to install Node.js.
 *
 * This code is a reduced copy of the ACP agent runtime manager in
 * `plugins/air/backend/acp/runtime/src/com/intellij/air/acp/runtime/registry/AcpRuntimeManager.kt`.
 * Read the `CLAUDE.md` next to that file for the decisions behind the staging directory, the pinned
 * checksum and the version sweep. Three things are cut on purpose:
 * - **The local machine only.** The original keys every path by an EEL descriptor, because an agent
 *   can run on WSL or in Docker. This copy resolves the host with [OS] and [CpuArch]. A project on
 *   WSL gets the Windows runtime.
 * - **No `npm install` stage.** The original installs the agent package into its own folder and runs
 *   it with `npx --no`. This copy provides the runtime only, so a caller still uses `npx --yes`, which
 *   downloads the server package on the first launch.
 * - **The managed runtime only.** A `node` on the local `PATH` is never found and never used. A
 *   process gets the runtime and the operating system shell on its `PATH`, and nothing else. See
 *   [managedPathValue].
 *
 * @see LspNodeRuntime
 */
@Service(Service.Level.APP)
@ApiStatus.Internal
@ApiStatus.Experimental
class LspNodeRuntimeManager {

  companion object {
    private const val NODE_DOWNLOAD_BASE = "https://nodejs.org/dist"

    /**
     * Suffix of the sibling staging directory an install is built in, before the atomic rename onto
     * the final version directory. See [stagingDirFor] and [promoteStagedInstall].
     */
    private const val INCOMPLETE_INSTALL_DIR_SUFFIX = ".incomplete"

    /**
     * Generous cap for the post-install `--version` smoke test. On Windows the first run can be slow,
     * because antivirus scans the freshly extracted `node_modules`. A false timeout discards a good
     * install and starts a new download, so this errs high.
     */
    private const val INSTALL_VALIDATION_TIMEOUT_MS = 30_000

    /**
     * The pinned Node.js version. 24.19.0 is the LTS line (Krypton).
     *
     * Change this together with [PLATFORM_TO_NODE_ARTIFACT].
     */
    const val NODE_VERSION: String = "24.19.0"

    /**
     * The Node.js distribution archives for [NODE_VERSION]: the `<os>-<arch>` file name part, plus
     * the SHA-256 the download is verified against. This map also states which hosts Node.js is
     * available on.
     *
     * You MUST update this together with [NODE_VERSION]. Replace every hash with the one from
     * `https://nodejs.org/dist/v<version>/SHASUMS256.txt`.
     */
    private val PLATFORM_TO_NODE_ARTIFACT: Map<LspNodeHost, NodeArtifact> = mapOf(
      LspNodeHost.MACOS_ARM64 to NodeArtifact("darwin-arm64", "8294b7aa9b03997481c06babf1e8b270c859358f27da57a11509afe537ac381d"),
      LspNodeHost.MACOS_X86_64 to NodeArtifact("darwin-x64", "d1b5e999db158c62fe8f7267a4476b035d8bd93b1a605bac24a3f0dd166e3316"),
      LspNodeHost.LINUX_ARM64 to NodeArtifact("linux-arm64", "d28c8a5bf0a808f0ed434a1dce8c54ae98f0371c0bd86ac58abc613f73e6643f"),
      LspNodeHost.LINUX_X86_64 to NodeArtifact("linux-x64", "f625d97cd707df4ff96254916fbc5ff014f09c09effe5a1e0ca8f6d41a8789d4"),
      LspNodeHost.WINDOWS_X86_64 to NodeArtifact("win-x64", "57f71ab3652e797d84acddc79c81cc9ff1c6ddb2a1974cdb83f00fee9bff4c73"),
      LspNodeHost.WINDOWS_ARM64 to NodeArtifact("win-arm64", "8502f4a50b458d4cc38ed8f2001556c2cd239d464920f74017926ccb1e1c157f"),
    )

    fun getInstance(): LspNodeRuntimeManager = service()

    /** The directory that holds one version directory per downloaded runtime. */
    @VisibleForTesting
    internal fun runtimesDir(): Path = PathManager.getSystemDir().resolve("lsp-client").resolve("node")

    /**
     * The archive name for [host], or `null` when Node.js has no distribution for it.
     */
    @VisibleForTesting
    internal fun archiveFileName(host: LspNodeHost): String? {
      val prefix = distributionPrefix(host) ?: return null
      return "$prefix.${if (host.isWindows) "zip" else "tar.gz"}"
    }

    /**
     * The single directory the archive wraps everything in. Extraction strips it.
     */
    private fun distributionPrefix(host: LspNodeHost): String? {
      val artifact = PLATFORM_TO_NODE_ARTIFACT[host] ?: return null
      return "node-v$NODE_VERSION-${artifact.platformSuffix}"
    }

    /**
     * Finds an executable in the Node.js distribution layout under [versionDir]:
     * `<name>.cmd` or `<name>.exe` at the root on Windows, next to `node_modules/`, and `bin/<name>`
     * on Unix, where npm reaches `node_modules/` through its own `../lib/node_modules` lookup.
     */
    @VisibleForTesting
    internal fun findNodeExecutable(versionDir: Path, name: String, host: LspNodeHost): Path? {
      val execHome = if (host.isWindows) versionDir else versionDir.resolve("bin")
      if (!execHome.exists()) return null

      for (ext in if (host.isWindows) listOf(".exe", ".cmd", ".bat") else listOf("")) {
        val exec = execHome.resolve("$name$ext")
        if (exec.exists() && Files.isRegularFile(exec)) return exec
      }
      return null
    }

    /**
     * The sibling staging directory an install is built in, before the atomic rename onto
     * [installDir]. It sits next to [installDir], so the rename stays on one filesystem. Its name is
     * predictable, so that even a leftover from a hard crash is reclaimed by the next
     * [cleanRuntimeDir].
     */
    @VisibleForTesting
    internal fun stagingDirFor(installDir: Path): Path =
      installDir.resolveSibling(installDir.fileName.toString() + INCOMPLETE_INSTALL_DIR_SUFFIX)

    /**
     * Prepares the staging [dir] for a new install. It deletes whatever an interrupted attempt left
     * there, which is about 100 MB for Node.js. The package cache next to it goes with it, and npm
     * builds the cache again on the first use.
     *
     * Extraction over leftovers is not an option. An overwrite of a file that antivirus or the Search
     * Indexer holds open fails the whole install.
     *
     * @return false if the cleanup failed, for example because a file is still locked. The caller must
     * stop the install and try again later, instead of extraction into a dirty directory.
     */
    @VisibleForTesting
    internal fun cleanRuntimeDir(dir: Path): Boolean {
      try {
        if (dir.exists()) {
          val leftovers = Files.list(dir).use { stream -> stream.map { it.fileName.toString() }.toList() }
          logger.info("Cleaning the staging dir before an install: $dir, leftovers: $leftovers")
        }
        NioFiles.deleteRecursively(dir)
        Files.createDirectories(dir)
        return true
      }
      catch (e: Exception) {
        logger.warn("Failed to clean the staging dir before an install: $dir", e)
        return false
      }
    }

    /**
     * Best-effort removal of a staging directory, called from the install's `finally`. It does nothing
     * after [promoteStagedInstall] renamed the directory away, because the path is gone. In every other
     * case it removes the partial tree a failed install left. It never throws, because a cleanup failure
     * must not hide the original result.
     */
    private fun deleteStagingQuietly(stagingDir: Path) {
      try {
        NioFiles.deleteRecursively(stagingDir)
      }
      catch (e: Exception) {
        logger.error("Failed to clean up the staging dir $stagingDir", e)
      }
    }

    /**
     * Publishes a complete install. It renames [stagingDir] onto [installDir] in one step, so
     * [findRuntimeInVersionDir] never sees a partial tree and no completion marker is necessary.
     *
     * It removes an existing [installDir] first, because [StandardCopyOption.ATOMIC_MOVE] refuses to
     * replace a directory. Such a directory is a leftover, never a usable runtime, because
     * [getRuntime] returns before the download in that case.
     *
     * @return false if the rename failed. The staged directory stays for the next install to clean.
     */
    @VisibleForTesting
    internal fun promoteStagedInstall(stagingDir: Path, installDir: Path): Boolean {
      return try {
        if (installDir.exists()) {
          logger.info("Removing the existing runtime dir before the staged install goes in: $installDir")
          NioFiles.deleteRecursively(installDir)
        }
        try {
          Files.move(stagingDir, installDir, StandardCopyOption.ATOMIC_MOVE)
        }
        catch (e: AtomicMoveNotSupportedException) {
          // Every filesystem we install on supports an atomic move, so this must not happen. It is
          // logged as an error to learn about the environment. The staged dir is complete, so a plain
          // rename on the same filesystem still publishes a complete install.
          logger.error("An atomic move is unexpectedly unsupported for $stagingDir -> $installDir. Falling back to a plain move.", e)
          Files.move(stagingDir, installDir)
        }
        logger.debug { "Promoted the staged install $stagingDir -> $installDir" }
        true
      }
      catch (e: Exception) {
        logger.warn("Failed to promote the staged install $stagingDir -> $installDir", e)
        false
      }
    }

    /**
     * Removes every version directory that is not [NODE_VERSION]. It runs once, after a successful
     * install of that pin.
     *
     * [getRuntime] only looks at the pinned version, so every other tree is dead weight, which is
     * about 100 MB per Node.js release.
     *
     * The ACP manager removes only *older* versions, because it shares its directory with a second
     * copy of that engine that pins its own version. This directory has one owner, so it removes every
     * other version.
     *
     * Best-effort: a locked file must not fail the install that just succeeded.
     */
    @VisibleForTesting
    internal fun deleteOtherRuntimeVersions(runtimesDir: Path) {
      try {
        val stale = Files.list(runtimesDir).use { stream ->
          stream.filter { candidate -> isStaleRuntimeVersionDir(candidate) }.toList()
        }
        for (dir in stale) {
          logger.info("Removing a superseded Node.js runtime: $dir")
          NioFiles.deleteRecursively(dir)
        }
      }
      catch (e: Exception) {
        logger.warn("Failed to remove the superseded Node.js runtimes under $runtimesDir", e)
      }
    }

    /**
     * Whether [candidate] is a version directory that [NODE_VERSION] replaces.
     *
     * A staging directory belongs to an install in flight, so it stays. [VersionComparatorUtil] keeps
     * a directory with a name this code cannot read, instead of a removal of something unknown.
     */
    private fun isStaleRuntimeVersionDir(candidate: Path): Boolean {
      val name = candidate.fileName.toString()
      return !name.endsWith(INCOMPLETE_INSTALL_DIR_SUFFIX) && VersionComparatorUtil.compare(name, NODE_VERSION) != 0
    }

    /** Makes [path] executable on Unix. */
    private fun makeExecutable(path: Path, host: LspNodeHost) {
      if (host.isWindows) return

      try {
        val permissions = Files.getPosixFilePermissions(path).toMutableSet()
        permissions.addAll(setOf(
          PosixFilePermission.OWNER_EXECUTE,
          PosixFilePermission.GROUP_EXECUTE,
          PosixFilePermission.OTHERS_EXECUTE,
        ))
        Files.setPosixFilePermissions(path, permissions)
      }
      catch (e: Exception) {
        logger.warn("Failed to set the executable permission on $path", e)
      }
    }

    /**
     * Verifies the SHA-256 of [archivePath] against [expectedChecksum], which is lowercase hex, as
     * [sha256Hex] returns. It returns false on a mismatch, so the caller can fail the install.
     */
    private fun verifyChecksum(archivePath: Path, fileName: String, expectedChecksum: String): Boolean {
      val actualChecksum = sha256Hex(archivePath)
      if (actualChecksum != expectedChecksum) {
        logger.warn("Checksum mismatch for $fileName: expected $expectedChecksum, got $actualChecksum")
        return false
      }
      return true
    }

    /**
     * Runs `<executable> --version` to prove that the install is complete, and that it runs through the
     * same entry point a server uses. This catches what a file existence check cannot: a partial
     * extraction, a binary for the wrong architecture, an executable that antivirus blocks.
     *
     * @return the version the executable reports, or null if it failed to run
     */
    private fun runVersionCheck(executable: Path, runtimeDir: Path): String? {
      val commandLine = GeneralCommandLine(executable.toString(), "--version")
      for ((key, value) in nodeEnvironmentVariables(runtimeDir)) {
        commandLine.withEnvironment(key, value)
      }
      commandLine.withEnvironment("PATH", managedPathValue(executable.parent))

      val commandDescription = commandLine.commandLineString
      return try {
        val startedAt = System.currentTimeMillis()
        val output = CapturingProcessHandler(commandLine).runProcess(INSTALL_VALIDATION_TIMEOUT_MS)
        when {
          output.isTimeout -> {
            logger.warn("The runtime check timed out after ${INSTALL_VALIDATION_TIMEOUT_MS}ms: $commandDescription")
            null
          }
          output.exitCode != 0 -> {
            logger.warn("The runtime check failed with exit code ${output.exitCode}: $commandDescription\n${output.stderr.trim()}")
            null
          }
          else -> {
            val reportedVersion = output.stdout.trim()
            logger.info("The runtime check passed in ${System.currentTimeMillis() - startedAt}ms: npx --version -> $reportedVersion")
            reportedVersion
          }
        }
      }
      catch (e: CancellationException) {
        // A cancelled check is not a failed check. Without this the install would report a broken
        // runtime and start a new download.
        throw e
      }
      catch (e: Exception) {
        logger.warn("The runtime check failed to run: $commandDescription", e)
        null
      }
    }

    /**
     * The environment a process needs to use [runtimeDir] as its Node.js installation.
     *
     * `~/.npmrc` is left alone on purpose. A corporate registry mirror, its token and its certificate
     * live there. npm ranks an environment variable above every `.npmrc` file, so each key here is a
     * pin the user configuration cannot move.
     */
    fun nodeEnvironmentVariables(runtimeDir: Path): Map<String, String> = mapOf(
      "NPM_CONFIG_CACHE" to runtimeDir.resolve("npm-cache").toString(),
      "NPM_CONFIG_MIN_RELEASE_AGE" to "0",
      // A server package installs as published. A stricter user policy only fails it.
      "NPM_CONFIG_ENGINE_STRICT" to "false",
      "NPM_CONFIG_STRICT_PEER_DEPS" to "false",
      // Trust the OS certificate store too, so npm works behind a corporate TLS proxy.
      "NODE_USE_SYSTEM_CA" to "1",
    )
  }

  /**
   * Serializes the download, so one open file per editor tab cannot start several downloads of the
   * same archive.
   */
  private val downloadLock = ReentrantLock()

  /**
   * The runtime this session found or installed. The pin does not move while the IDE runs, so a hit
   * here keeps [getRuntime] free of I/O for every later call.
   *
   * A tree that something removes from outside therefore stays cached. The server then fails to start,
   * instead of a new download. The ACP manager accepts the same result for its package directory.
   */
  @Volatile
  private var cachedRuntime: LspNodeRuntime? = null

  /**
   * The runtime, if it is already on disk. This function never downloads, so it is safe to call from
   * [com.intellij.platform.lsp.api.LspIntegrationProvider.fileOpened], which holds the read lock.
   *
   * @return null when the runtime needs a download, or when Node.js has no distribution for this host
   */
  fun getRuntime(): LspNodeRuntime? {
    cachedRuntime?.let { return it }

    val host = LspNodeHost.current() ?: return null
    return findRuntimeInVersionDir(runtimesDir().resolve(NODE_VERSION), host)?.also { cachedRuntime = it }
  }

  /**
   * The runtime, downloaded if it is not on disk yet.
   *
   * @param indicator reports the download progress, and cancels it
   * @return null if the download failed, or if Node.js has no distribution for this host
   */
  @RequiresBackgroundThread
  fun ensureRuntime(indicator: ProgressIndicator? = null): LspNodeRuntime? {
    getRuntime()?.let { return it }

    return downloadLock.withLock {
      // Another thread may have finished the download while this one waited for the lock.
      getRuntime()?.let { return@withLock it }

      val host = LspNodeHost.current() ?: run {
        logger.warn("Node.js has no distribution for this host: ${LspNodeHost.currentDescription()}")
        return@withLock null
      }
      val runtimesDir = runtimesDir()
      val installDir = runtimesDir.resolve(NODE_VERSION)
      if (!installRuntime(host, installDir, indicator)) return@withLock null

      // The pin just moved, which is the one moment the trees it replaces are known to be unused. This
      // runs inside the lock, so no other install can look at them.
      deleteOtherRuntimeVersions(runtimesDir)

      findRuntimeInVersionDir(installDir, host)?.also { cachedRuntime = it }
    }
  }

  /**
   * Finds a runtime in one version directory.
   *
   * The version directory only ever appears complete. An install is built in a sibling
   * `<version>.incomplete` directory, and renamed into place as the last step. See
   * [promoteStagedInstall]. The directory together with its executable is therefore the whole
   * completeness signal.
   */
  private fun findRuntimeInVersionDir(versionDir: Path, host: LspNodeHost): LspNodeRuntime? {
    if (!versionDir.exists()) {
      logger.debug { "Node.js runtime lookup: the version dir does not exist: $versionDir" }
      return null
    }

    val node = findNodeExecutable(versionDir, "node", host)
    val npx = findNodeExecutable(versionDir, "npx", host)
    if (node == null || npx == null || !node.isExecutable()) {
      logger.debug { "Node.js runtime lookup: an executable is missing in $versionDir (node=$node, npx=$npx)" }
      return null
    }
    logger.debug { "Node.js runtime lookup: found a valid install, node=$node" }
    return LspNodeRuntime(NODE_VERSION, versionDir, node, npx, findNodeExecutable(versionDir, "npm", host))
  }

  /**
   * Downloads the archive for [host], verifies it, extracts it, checks it, and publishes it at
   * [installDir].
   *
   * @return true if [installDir] now holds a complete runtime
   */
  @Suppress("DialogTitleCapitalization")
  private fun installRuntime(host: LspNodeHost, installDir: Path, indicator: ProgressIndicator?): Boolean {
    val artifact = PLATFORM_TO_NODE_ARTIFACT.getValue(host)
    val fileName = checkNotNull(archiveFileName(host))
    val distPrefix = checkNotNull(distributionPrefix(host))
    val url = "$NODE_DOWNLOAD_BASE/v$NODE_VERSION/$fileName"

    // The install is built in a sibling staging dir, and renamed onto installDir only after it is
    // complete and checked, so a concurrent lookup never sees a torn tree.
    val stagingDir = stagingDirFor(installDir)
    try {
      logger.info("Installing Node.js v$NODE_VERSION for $host from $url into $installDir")
      indicator?.isIndeterminate = false
      indicator?.text = LspBundle.message("lsp.runtime.node.progress.downloading", NODE_VERSION)

      if (!cleanRuntimeDir(stagingDir)) return false

      val archivePath = stagingDir.resolve(fileName)
      HttpRequests.request(url)
        .userAgent("JetBrains-LSP-Node-Runtime/1.0")
        .saveToFile(archivePath, indicator)

      indicator?.checkCanceled()
      indicator?.isIndeterminate = true
      indicator?.text = LspBundle.message("lsp.runtime.node.progress.verifying")
      if (!verifyChecksum(archivePath, fileName, artifact.sha256)) return false

      indicator?.checkCanceled()
      indicator?.text = LspBundle.message("lsp.runtime.node.progress.extracting")
      // Strip the `node-v<ver>-<os>-<arch>/` wrapper per entry during extraction, so the distribution
      // lands directly in the staging dir. This needs no move after the extraction, which is fragile
      // on Windows while antivirus holds a handle, and no search for the wrapper at lookup time. A
      // relative symlink inside the archive, for example `bin/npm` -> `../lib/node_modules/...`,
      // moves with it and stays valid.
      if (host.isWindows) {
        Decompressor.Zip(archivePath).removePrefixPath(distPrefix).extract(stagingDir)
      }
      else {
        Decompressor.Tar(archivePath).removePrefixPath(distPrefix).extract(stagingDir)
      }

      Files.deleteIfExists(archivePath)
      Files.createDirectories(stagingDir.resolve("npm-cache"))

      val nodeBin = findNodeExecutable(stagingDir, "node", host)
      val npxBin = findNodeExecutable(stagingDir, "npx", host)
      if (nodeBin == null || npxBin == null) {
        logger.warn("No Node.js executable after the extraction, prefix '$distPrefix' stripped: node=$nodeBin, npx=$npxBin in $stagingDir")
        return false
      }
      makeExecutable(nodeBin, host)
      makeExecutable(npxBin, host)
      findNodeExecutable(stagingDir, "npm", host)?.let { makeExecutable(it, host) }

      if (runVersionCheck(npxBin, stagingDir) == null) return false
      if (!promoteStagedInstall(stagingDir, installDir)) return false

      logger.info("Installed Node.js v$NODE_VERSION to $installDir")
      return true
    }
    catch (e: CancellationException) {
      // The user pressed Stop on the progress bar, or a caller's coroutine was cancelled.
      // `ProcessCanceledException` is a `CancellationException`, so one catch covers both, and
      // cancellation must never become a logged failure. The `finally` below removes the partial tree.
      throw e
    }
    catch (e: Exception) {
      logger.warn("Failed to download Node.js v$NODE_VERSION", e)
      return false
    }
    finally {
      // The rename takes the staging dir away on success. On a failure this removes the partial tree,
      // instead of a wait for the next install to clean it.
      deleteStagingQuietly(stagingDir)
    }
  }

  private data class NodeArtifact(val platformSuffix: String, val sha256: String)
}

/**
 * One installed Node.js runtime.
 *
 * @property version the Node.js version, which is always [LspNodeRuntimeManager.NODE_VERSION]
 * @property runtimeDir the root of the installation, next to its `npm-cache/`
 * @property node the `node` executable
 * @property npx the `npx` executable, which a server descriptor launches
 * @property npm the `npm` executable, absent in a distribution that ships without it
 */
@ApiStatus.Internal
@ApiStatus.Experimental
class LspNodeRuntime(
  val version: String,
  val runtimeDir: Path,
  val node: Path,
  val npx: Path,
  val npm: Path?,
) {
  /** The directory that holds [node], [npx] and [npm]. */
  val binDir: Path get() = node.parent

  /** @see LspNodeRuntimeManager.nodeEnvironmentVariables */
  fun environmentVariables(): Map<String, String> = LspNodeRuntimeManager.nodeEnvironmentVariables(runtimeDir)

  /**
   * Applies this runtime to [commandLine]: the npm configuration, and [managedPathValue] as `PATH`.
   */
  fun applyTo(commandLine: GeneralCommandLine): GeneralCommandLine {
    for ((key, value) in environmentVariables()) {
      commandLine.withEnvironment(key, value)
    }
    return commandLine.withEnvironment("PATH", managedPathValue(binDir))
  }
}

/**
 * A host Node.js publishes a distribution for.
 *
 * The ACP manager this code comes from resolves the host through an EEL descriptor, so it can install
 * a runtime on WSL or in Docker. This copy reads the local machine, so a project on WSL gets the
 * Windows runtime.
 */
internal enum class LspNodeHost(val isWindows: Boolean) {
  MACOS_ARM64(false),
  MACOS_X86_64(false),
  LINUX_ARM64(false),
  LINUX_X86_64(false),
  WINDOWS_X86_64(true),
  WINDOWS_ARM64(true),
  ;

  companion object {
    /**
     * The local host, or null when Node.js publishes no distribution for it.
     */
    @OptIn(LowLevelLocalMachineAccess::class)
    fun current(): LspNodeHost? = of(OS.CURRENT, CpuArch.CURRENT)

    /** The local OS and architecture, for a log message about an unsupported host. */
    @OptIn(LowLevelLocalMachineAccess::class)
    fun currentDescription(): String = "${OS.CURRENT} ${CpuArch.CURRENT}"

    @VisibleForTesting
    internal fun of(os: OS, arch: CpuArch): LspNodeHost? = when (os) {
      OS.macOS -> when (arch) {
        CpuArch.ARM64 -> MACOS_ARM64
        CpuArch.X86_64 -> MACOS_X86_64
        else -> null
      }
      OS.Linux -> when (arch) {
        CpuArch.ARM64 -> LINUX_ARM64
        CpuArch.X86_64 -> LINUX_X86_64
        else -> null
      }
      OS.Windows -> when (arch) {
        CpuArch.ARM64 -> WINDOWS_ARM64
        CpuArch.X86_64 -> WINDOWS_X86_64
        else -> null
      }
      else -> null
    }
  }
}
