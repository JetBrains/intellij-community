// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
@file:JvmName("DevDataLink")
@file:OptIn(LowLevelLocalMachineAccess::class)
// The bootstrap runs before any Eel or IJent file system exists, so the plain NIO calls are the right ones.
@file:Suppress("UseOptimizedEelFunctions")
package com.intellij.idea

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.HexFormat
import java.util.TreeSet
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.invariantSeparatorsPathString

/** The workspace-relative path of the dev data of the dev IDEs that a checkout starts. */
const val DEV_DATA_LINK: String = "out/dev-data"

/** The file in a dev-data root that holds the real path of the checkout that owns the root. */
const val DEV_DATA_WORKSPACE_MARKER: String = ".workspace"

/** The environment variable that replaces the default parent of the dev-data roots. */
const val DEV_DATA_ROOT_VARIABLE: String = "INTELLIJ_DEV_DATA_ROOT"

/**
 * Returns the dev-data root of the checkout at [workspace], or `null` when the dev data stays in the workspace.
 *
 * [workspace] is a real path. The name of the root is the name of the checkout and 8 hex digits of its path hash.
 */
fun devDataRoot(workspace: Path, getenv: (String) -> String?, os: OS): Path? {
  return devDataParent(getenv, os)?.let { devDataRootIn(it, workspace) }
}

private fun devDataRootIn(parent: Path, workspace: Path): Path {
  val digest = MessageDigest.getInstance("SHA-256").digest(workspace.invariantSeparatorsPathString.toByteArray(Charsets.UTF_8))
  val hash = HexFormat.of().formatHex(digest, 0, 4)
  return parent.resolve("${workspace.fileName?.toString().orEmpty()}-$hash")
}

private fun devDataParent(getenv: (String) -> String?, os: OS): Path? {
  if (os == OS.Windows) {
    return null
  }
  getenv(DEV_DATA_ROOT_VARIABLE)?.takeIf { it.isNotEmpty() }?.let {
    return Path.of(it)
  }
  val home = getenv("HOME")
  if (os != OS.macOS || home.isNullOrEmpty()) {
    return null
  }
  return Path.of(home, "Library", "Caches", "JetBrains", "MonorepoDevData")
}

/**
 * Makes `out/dev-data` in [workspace] a symbolic link to the dev-data root of the checkout.
 *
 * The function moves the dev data of an older launch out of the workspace first.
 * It also deletes the stale launcher homes under `out/dev-data/<row>/homes`.
 * It never fails a launch. When it cannot make the link, it calls [warn], and the IDE uses the directory in the workspace.
 */
fun ensureDevDataLink(
  workspace: String,
  getenv: (String) -> String? = System::getenv,
  os: OS = OS.CURRENT,
  warn: (String) -> Unit = System.err::println,
) {
  try {
    val workspacePath = Path.of(workspace)
    val link = workspacePath.resolve(DEV_DATA_LINK)
    linkDevData(link = link, workspace = workspacePath.toRealPath(), getenv = getenv, os = os, warn = warn)
    removeStaleLauncherHomes(link, warn)
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    warn("WARNING: cannot check the dev data link of $workspace: $e")
  }
}

private fun linkDevData(link: Path, workspace: Path, getenv: (String) -> String?, os: OS, warn: (String) -> Unit) {
  val parent = devDataParent(getenv, os) ?: return
  // A launch over an existing link needs no hash.
  val root = lazy(LazyThreadSafetyMode.NONE) { devDataRootIn(parent, workspace) }
  // Two launches can race for the same link. The loser sees the entry appear or disappear and looks again.
  var attempt = 1
  while (true) {
    try {
      linkDevDataOnce(link = link, workspace = workspace, root = root, warn = warn)
      return
    }
    catch (e: IOException) {
      val race = e is FileAlreadyExistsException || e is DirectoryNotEmptyException || e is NoSuchFileException
      if (!race || attempt == 3) {
        warn("WARNING: the dev data stays in $link: ${if (e is KeepDevData) e.message else e.toString()}")
        return
      }
    }
    attempt++
  }
}

private fun linkDevDataOnce(link: Path, workspace: Path, root: Lazy<Path>, warn: (String) -> Unit) {
  val out = link.parent
  val realOut = try {
    out.toRealPath()
  }
  catch (_: IOException) {
    null
  }
  if (realOut != null && !realOut.startsWith(workspace)) {
    // The user keeps `out` outside the workspace already, so the writes of the IDE do not reach the file watcher of Bazel.
    return
  }

  val attributes = try {
    Files.readAttributes(link, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
  }
  catch (_: NoSuchFileException) {
    createDevDataLink(link = link, workspace = workspace, root = root.value, warn = warn)
    return
  }

  if (attributes.isSymbolicLink) {
    // The link decides, not the formula, so a moved checkout keeps its data.
    val target = out.resolve(Files.readSymbolicLink(link))
    // A cache cleaner can remove the target. The IDE then starts with an empty config, as on a first launch.
    Files.createDirectories(target)
    writeWorkspaceMarker(target, workspace)
    return
  }
  if (!attributes.isDirectory) {
    throw KeepDevData("$link is not a directory")
  }
  moveDevData(link = link, workspace = workspace, root = root.value)
}

private fun createDevDataLink(link: Path, workspace: Path, root: Path, warn: (String) -> Unit) {
  val created = Files.notExists(root)
  Files.createDirectories(root)
  writeWorkspaceMarker(root, workspace)
  Files.createDirectories(link.parent)
  Files.createSymbolicLink(link, root)
  if (created) {
    reportOrphanRoots(parent = root.parent, current = root, warn = warn)
  }
}

/**
 * Moves the directory [link] to [root] and replaces it with a link.
 *
 * A root that holds entries already gets the entries that it lacks.
 * The dev data stays when a dev IDE runs from it, when an entry exists on both sides, or when the root is on another volume.
 */
private fun moveDevData(link: Path, workspace: Path, root: Path) {
  liveDevIdeRow(link)?.let { row ->
    throw KeepDevData("a dev IDE runs from ${link.resolve(row)}. Stop it to move the dev data out of the workspace")
  }
  val same = try {
    Files.isSameFile(link, root)
  }
  catch (_: IOException) {
    false
  }
  if (same) {
    // A concurrent launch has moved the directory and made the link since this launch looked.
    return
  }

  Files.createDirectories(root.parent)
  val entries = try {
    devDataEntries(root)
  }
  catch (_: NoSuchFileException) {
    emptySet()
  }
  if (entries.isEmpty()) {
    // A root with only its marker is empty. A rename replaces an empty directory but not one with a file in it.
    Files.deleteIfExists(root.resolve(DEV_DATA_WORKSPACE_MARKER))
    renameDevData(link, root, link = link, root = root)
  }
  else {
    val legacy = devDataEntries(link)
    val both = legacy.filter { it in entries }
    if (both.isNotEmpty()) {
      if (Files.isSymbolicLink(link)) {
        // A concurrent launch has made the link, so the entries of the link are the entries of the root. Look again.
        throw NoSuchFileException(link.toString())
      }
      throw KeepDevData("$link and $root both hold ${both.joinToString(", ")}. Merge them by hand")
    }
    for (name in legacy) {
      try {
        renameDevData(link.resolve(name), root.resolve(name), link = link, root = root)
      }
      catch (_: NoSuchFileException) {
        // A concurrent launch has moved this entry already.
      }
    }
    Files.delete(link)
  }
  writeWorkspaceMarker(root, workspace)
  Files.createSymbolicLink(link, root)
}

/** Renames [source] to [target] with one `rename` call, so the move of a large directory takes no copy. */
private fun renameDevData(source: Path, target: Path, link: Path, root: Path) {
  try {
    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
  }
  catch (_: AtomicMoveNotSupportedException) {
    throw KeepDevData("$root is on another volume than $link. Stop the dev IDEs, then run: mv $link $root && ln -s $root $link")
  }
  catch (e: FileSystemException) {
    // The JDK reports `ENOTEMPTY` of `rename` as a plain `FileSystemException`. A concurrent launch can fill the target.
    if (e.javaClass == FileSystemException::class.java && Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
      throw DirectoryNotEmptyException(target.toString()).apply { initCause(e) }
    }
    throw e
  }
}

/** Returns the names of the entries in [directory], except [DEV_DATA_WORKSPACE_MARKER]. */
private fun devDataEntries(directory: Path): Set<String> {
  val names = TreeSet<String>()
  Files.newDirectoryStream(directory).use { stream ->
    for (entry in stream) {
      val name = entry.fileName.toString()
      if (name != DEV_DATA_WORKSPACE_MARKER) {
        names.add(name)
      }
    }
  }
  return names
}

/**
 * Returns the name of a row in [devData] whose IDE still runs.
 *
 * An IDE writes its process ID to `config/.lock` (`DirectoryLock`).
 */
private fun liveDevIdeRow(devData: Path): String? {
  Files.newDirectoryStream(devData).use { stream ->
    for (row in stream) {
      if (!Files.isDirectory(row, LinkOption.NOFOLLOW_LINKS)) {
        continue
      }
      val pid = try {
        Files.readString(row.resolve("config").resolve(".lock")).trim()
      }
      catch (_: IOException) {
        continue
      }
      if (isOtherLiveProcess(pid)) {
        return row.fileName.toString()
      }
    }
  }
  return null
}

private fun isOtherLiveProcess(pid: String): Boolean {
  val value = pid.toLongOrNull() ?: return false
  return value != ProcessHandle.current().pid() && isLiveProcess(value)
}

private fun isLiveProcess(pid: Long): Boolean = ProcessHandle.of(pid).orElse(null)?.isAlive == true

private fun writeWorkspaceMarker(root: Path, workspace: Path) {
  val marker = root.resolve(DEV_DATA_WORKSPACE_MARKER)
  val content = "$workspace\n"
  val current = try {
    Files.readString(marker)
  }
  catch (_: IOException) {
    null
  }
  if (current != content) {
    Files.writeString(marker, content)
  }
}

/**
 * Reports each root in [parent] whose checkout no longer exists.
 *
 * The function removes nothing. A moved checkout that has not launched since the move still holds its old path.
 */
private fun reportOrphanRoots(parent: Path, current: Path, warn: (String) -> Unit) {
  val roots = try {
    Files.newDirectoryStream(parent).use { it.toList() }
  }
  catch (_: IOException) {
    return
  }
  for (root in roots) {
    if (root == current || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
      continue
    }
    val marker = try {
      Files.readString(root.resolve(DEV_DATA_WORKSPACE_MARKER)).trim()
    }
    catch (_: IOException) {
      continue
    }
    if (marker.isEmpty()) {
      continue
    }
    val workspace = Path.of(marker)
    // A checkout whose parent is missing can be on a volume that is not mounted.
    if (Files.exists(workspace) || workspace.parent?.let { Files.exists(it) } != true) {
      continue
    }
    warn("NOTE: the dev data $root belongs to the checkout $workspace, which no longer exists. To free the space, run: rm -rf $root")
  }
}

/**
 * Deletes each launcher home `<row>/homes/<pid>` in [devData] whose process no longer runs.
 *
 * The dev launcher of decision 2026-11-09-a-dev-launch-starts-the-production-launcher-from-a-generation made these homes. A launch over a runfiles home makes none.
 * The function deletes an empty `homes` directory too, and nothing else.
 */
@OptIn(ExperimentalPathApi::class)
private fun removeStaleLauncherHomes(devData: Path, warn: (String) -> Unit) {
  val rows = try {
    Files.newDirectoryStream(devData).use { it.toList() }
  }
  catch (_: IOException) {
    return
  }
  for (row in rows) {
    val homes = row.resolve("homes")
    if (!Files.isDirectory(row, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(homes, LinkOption.NOFOLLOW_LINKS)) {
      continue
    }
    try {
      val entries = Files.newDirectoryStream(homes).use { it.toList() }
      for (home in entries) {
        val pid = home.fileName.toString().toLongOrNull()
        if (pid == null || pid == ProcessHandle.current().pid() || isLiveProcess(pid)) {
          continue
        }
        // The function does not follow a symbolic link, so it deletes the links of a home and never their targets.
        home.deleteRecursively()
      }
      Files.delete(homes)
    }
    catch (_: DirectoryNotEmptyException) {
      // A dev IDE that a launcher started before this change can still hold the directory.
    }
    catch (e: IOException) {
      warn("WARNING: cannot remove the stale dev homes in $homes: $e")
    }
  }
}

/** A reason to keep the dev data in the workspace for this launch. It is a warning, not a failure. */
private class KeepDevData(message: String) : IOException(message)
